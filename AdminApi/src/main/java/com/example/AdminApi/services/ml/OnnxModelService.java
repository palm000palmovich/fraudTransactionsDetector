package com.example.AdminApi.services.ml;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * Сервис для работы с ONNX моделью fraud detection.
 *
 * Особенности:
 * - Загрузка модели при старте приложения (singleton)
 * - Прогрев (warm-up) для инициализации JIT
 * - Thread-safe (session immutable после загрузки)
 * - Мягкий fallback при ошибках
 *
 * Performance:
 * - Загрузка: ~100-200ms (один раз при старте)
 * - Inference: ~5-10ms на CPU
 */
@Slf4j
@Service
public class OnnxModelService {

    private OrtSession session;
    private OrtEnvironment env;

    private final ResourceLoader resourceLoader;

    @Value("${ml.model.path}")
    private String modelPath;

    @Value("${ml.model.version}")
    private String modelVersion;

    public OnnxModelService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Загрузка модели при старте приложения.
     * Singleton - загружается один раз и хранится в памяти.
     */
    @PostConstruct
    public void loadModel() {
        try {
            log.info("Loading ONNX model: version={}, path={}", modelVersion, modelPath);
            long startTime = System.currentTimeMillis();

            env = OrtEnvironment.getEnvironment();

            // Загружаем модель из resources
            Resource resource = resourceLoader.getResource(modelPath);
            byte[] modelBytes = resource.getContentAsByteArray();

            log.info("Model file loaded: {} bytes", modelBytes.length);

            // Создаем сессию с дефолтными опциями
            session = env.createSession(modelBytes, new OrtSession.SessionOptions());

            long loadTime = System.currentTimeMillis() - startTime;
            log.info("ONNX session created in {}ms", loadTime);

            // Прогрев (warm-up) - инициализация JIT
            warmUp();

            log.info("ONNX model loaded successfully: version={}, inputs={}, outputs={}, total time={}ms",
                modelVersion, session.getInputNames(), session.getOutputNames(),
                System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("Failed to load ONNX model from path: {}", modelPath, e);
            throw new RuntimeException("ML model initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Прогрев модели - выполняем 3 инференса с dummy данными.
     * Это инициализирует JIT и кэширует операции для более быстрой работы.
     */
    private void warmUp() {
        try {
            log.info("Warming up ONNX model...");
            long startTime = System.currentTimeMillis();

            float[] dummyFeatures = new float[15]; // production-v1 model expects 15 features

            for (int i = 0; i < 3; i++) {
                predict(dummyFeatures);
            }

            long warmUpTime = System.currentTimeMillis() - startTime;
            log.info("Model warm-up completed in {}ms", warmUpTime);

        } catch (Exception e) {
            log.warn("Model warm-up failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Inference: получить вероятность мошенничества.
     *
     * Thread-safe: session immutable после загрузки.
     *
     * @param features Вектор из 10 features
     * @return Вероятность мошенничества (0.0 - 1.0)
     * @throws OrtException если inference не удался
     */
    public float predict(float[] features) throws OrtException {
        if (features.length != 15) {
            throw new IllegalArgumentException("Expected 15 features (production-v1), got " + features.length);
        }

        if (session == null) {
            throw new IllegalStateException("ONNX model not loaded");
        }

        long startTime = System.nanoTime();

        OnnxTensor tensor = null;
        OrtSession.Result result = null;

        try {
            // Создаем тензор: shape = [1, 10] (batch_size=1, features=10)
            long[] shape = {1, features.length};
            tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape);

            // Inference (имя input: "features" - должно совпадать с моделью)
            Map<String, OnnxTensor> inputs = Map.of("features", tensor);
            result = session.run(inputs);

            // Извлекаем результат
            // Output 0 = label (long), Output 1 = probabilities
            // Для sklearn моделей probabilities может быть OnnxSequence или напрямую OnnxMap
            OnnxValue probsValue = result.get(1);

            float score;

            // Пробуем разные варианты структуры выхода
            if (probsValue instanceof ai.onnxruntime.OnnxSequence) {
                // Вариант 1: Sequence[Map<Long, Float>]
                ai.onnxruntime.OnnxSequence probsSeq = (ai.onnxruntime.OnnxSequence) probsValue;
                java.util.List<? extends OnnxValue> probsListRaw = probsSeq.getValue();

                ai.onnxruntime.OnnxMap firstProbMap = (ai.onnxruntime.OnnxMap) probsListRaw.get(0);
                @SuppressWarnings("unchecked")
                java.util.Map<Long, Float> probabilities = (java.util.Map<Long, Float>) firstProbMap.getValue();
                score = probabilities.getOrDefault(1L, 0.0f);

            } else if (probsValue instanceof ai.onnxruntime.OnnxMap) {
                // Вариант 2: напрямую Map<Long, Float>
                ai.onnxruntime.OnnxMap probsMap = (ai.onnxruntime.OnnxMap) probsValue;
                @SuppressWarnings("unchecked")
                java.util.Map<Long, Float> probabilities = (java.util.Map<Long, Float>) probsMap.getValue();
                score = probabilities.getOrDefault(1L, 0.0f);

            } else {
                // Вариант 3: OnnxTensor с вероятностями
                throw new IllegalStateException(
                    "Unexpected output type: " + probsValue.getClass().getName() +
                    ". Expected OnnxSequence or OnnxMap"
                );
            }

            long inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000;

            if (log.isDebugEnabled()) {
                log.debug("Inference completed in {}ms, score={:.3f}", inferenceTimeMs, score);
            }

            return score;

        } catch (Exception e) {
            log.error("Inference failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Освобождаем ресурсы
            if (tensor != null) {
                tensor.close();
            }
            if (result != null) {
                result.close();
            }
        }
    }

    /**
     * Получить версию модели.
     */
    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * Проверить, загружена ли модель.
     */
    public boolean isModelLoaded() {
        return session != null;
    }

    /**
     * Очистка ресурсов при остановке приложения.
     */
    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }
            log.info("ONNX model resources cleaned up");
        } catch (Exception e) {
            log.warn("Error cleaning up ONNX resources: {}", e.getMessage());
        }
    }
}
