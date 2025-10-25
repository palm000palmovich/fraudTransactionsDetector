// Login functionality
document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.getElementById('loginForm');
    const submitButton = loginForm.querySelector('button[type="submit"]');

    // Update demo hint with correct credentials
    const demoHint = document.querySelector('.demo-hint');
    if (demoHint) {
        demoHint.innerHTML = `
            Тестовые учетные данные:<br>
            👤 ADMIN: <strong>admin</strong> / <strong>admin123</strong><br>
            👁️ VIEWER: <strong>viewer</strong> / <strong>viewer123</strong>
        `;
    }

    loginForm.addEventListener('submit', async function(e) {
        e.preventDefault();

        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        if (!username || !password) {
            alert('Пожалуйста, введите имя пользователя и пароль');
            return;
        }

        // Disable submit button during authentication
        submitButton.disabled = true;
        submitButton.textContent = 'Проверка...';

        try {
            const result = await Auth.testCredentials(username, password);

            if (result.success) {
                // Redirect to dashboard
                window.location.href = 'dashboard.html';
            } else {
                alert('Ошибка входа: ' + result.error);
                submitButton.disabled = false;
                submitButton.textContent = 'Войти';
            }
        } catch (error) {
            console.error('Login error:', error);
            alert('Ошибка при попытке входа: ' + error.message);
            submitButton.disabled = false;
            submitButton.textContent = 'Войти';
        }
    });
});
