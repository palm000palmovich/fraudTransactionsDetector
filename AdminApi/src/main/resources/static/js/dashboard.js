// API Configuration
const API_BASE = '/api';

// Data
let transactions = [];
let rules = [];
let auditLog = [];
let auditCurrentPage = 0;
let auditPageSize = 50;
let auditTotalPages = 0;
let notificationChannels = [
    { id: 1, name: 'Email', enabled: true, sent: 142, failed: 3, retrying: 1, avgLatency: 340 },
    { id: 2, name: 'Slack', enabled: true, sent: 89, failed: 2, retrying: 0, avgLatency: 180 },
    { id: 3, name: 'Webhook', enabled: false, sent: 67, failed: 3, retrying: 2, avgLatency: 285 },
];
let notifications = [
    { id: 'NOTIF-001', correlationId: 'CORR-001-2025-10-18', channel: 'Email', status: 'SENT', attempt: '1/3', duration: 320 },
    { id: 'NOTIF-002', correlationId: 'CORR-002-2025-10-18', channel: 'Slack', status: 'RETRYING', attempt: '2/3', duration: null },
    { id: 'NOTIF-003', correlationId: 'CORR-003-2025-10-18', channel: 'Webhook', status: 'FAILED', attempt: '3/3', duration: 450 },
];
let dlq = [];
let currentUser = null;
let editingRule = null;

// Pagination state
let currentPage = 0;
let pageSize = 10;
let totalPages = 0;

// Sorting state
let currentSort = 'newest'; // 'newest' or 'oldest'

// Filter state
let currentTimeRange = 'all';
let customDateFrom = null;
let customDateTo = null;

// Initialize
document.addEventListener('DOMContentLoaded', function() {
    // Check if user is authenticated using Auth module
    if (!Auth.isAuthenticated()) {
        window.location.href = 'login.html';
        return;
    }

    // Load user object from sessionStorage (for backward compatibility)
    const userStr = sessionStorage.getItem('user');
    if (userStr) {
        currentUser = JSON.parse(userStr);
    } else {
        // Fallback: create user object from Auth module data
        currentUser = {
            username: Auth.getUsername(),
            email: Auth.getUsername(),
            role: Auth.getRole()
        };
    }

    // Display user email and role
    const userEmail = document.getElementById('userEmail');
    if (userEmail) {
        userEmail.textContent = currentUser.email || currentUser.username;
    }

    const userRole = document.getElementById('userRole');
    if (userRole) {
        userRole.textContent = currentUser.role;
    }

    // Hide edit/delete buttons for viewers
    if (Auth.isViewer()) {
        applyViewerRestrictions();
    }

    // Initialize tabs
    initializeTabs();

    // Load initial data
    loadTransactions();
    loadRules();
    loadStats();
    loadAuditLog();
    loadNotifications();

    // Event listeners
    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    document.getElementById('filterStatus').addEventListener('change', loadTransactions);
    document.getElementById('searchCorrelation').addEventListener('input', loadTransactions);
    document.getElementById('searchFrom').addEventListener('input', loadTransactions);
    document.getElementById('searchTo').addEventListener('input', loadTransactions);

    // Event listeners for custom date range
    document.getElementById('dateFrom').addEventListener('change', () => {
        customDateFrom = document.getElementById('dateFrom').value;
        loadTransactions();
    });
    document.getElementById('dateTo').addEventListener('change', () => {
        customDateTo = document.getElementById('dateTo').value;
        loadTransactions();
    });

    // Event listener for rule params validation
    const ruleParamsTextarea = document.getElementById('ruleParams');
    if (ruleParamsTextarea) {
        ruleParamsTextarea.addEventListener('input', validateRuleParams);
        ruleParamsTextarea.addEventListener('blur', validateRuleParams);
    }
});

// Apply viewer restrictions (hide edit/delete buttons)
function applyViewerRestrictions() {
    // Hide "Create Rule" button
    const createRuleBtn = document.querySelector('button[onclick="openRuleEditor()"]');
    if (createRuleBtn) {
        createRuleBtn.style.display = 'none';
    }

    // Add a visual indicator that user is in read-only mode
    console.log('Viewer mode: Edit and delete buttons will be hidden');
}

// Tab switching
function initializeTabs() {
    const tabBtns = document.querySelectorAll('.tab-btn');
    const tabContents = document.querySelectorAll('.tab-content');

    tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const targetTab = btn.getAttribute('data-tab');

            // Remove active class from all
            tabBtns.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            // Add active class to current
            btn.classList.add('active');
            document.getElementById(targetTab).classList.add('active');
        });
    });
}

// Logout
function handleLogout() {
    Auth.logout(); // Use Auth module logout which clears everything
}

// Load statistics
async function loadStats() {
    try {
        const response = await Auth.fetch(`${API_BASE}/transactions/stats`);

        if (response.status === 403) {
            alert('У вас нет прав для просмотра статистики');
            return;
        }

        if (!response.ok) throw new Error('Failed to fetch stats');

        const stats = await response.json();

        // Update stats cards
        const totalCard = document.querySelector('.stat-card:nth-child(1) .stat-value');
        const processedCard = document.querySelector('.stat-card:nth-child(2) .stat-value');
        const alertedCard = document.querySelector('.stat-card:nth-child(3) .stat-value');
        const pendingCard = document.querySelector('.stat-card:nth-child(4) .stat-value');

        if (totalCard) totalCard.textContent = stats.totalTransactions.toLocaleString();
        if (processedCard) processedCard.textContent = stats.processedCount.toLocaleString();
        if (alertedCard) alertedCard.textContent = stats.alertedCount.toLocaleString();
        if (pendingCard) pendingCard.textContent = stats.pendingCount.toLocaleString();

        console.log('Stats loaded:', stats);
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

// Load transactions
async function loadTransactions() {
    const filterStatus = document.getElementById('filterStatus').value;
    const searchTerm = document.getElementById('searchCorrelation').value;
    const searchFrom = document.getElementById('searchFrom').value;
    const searchTo = document.getElementById('searchTo').value;

    try {
        let url = `${API_BASE}/transactions`;
        const params = new URLSearchParams();

        if (filterStatus !== 'all') {
            params.append('status', filterStatus.toUpperCase());
        }

        if (searchTerm) {
            params.append('correlationId', searchTerm);
        }

        if (searchFrom) {
            params.append('sourceId', searchFrom);
        }

        if (searchTo) {
            params.append('destinationId', searchTo);
        }

        // Add time range filters
        if (currentTimeRange !== 'all' && currentTimeRange !== 'custom') {
            const { dateFrom, dateTo } = calculateTimeRange(currentTimeRange);
            if (dateFrom) params.append('dateFrom', dateFrom);
            if (dateTo) params.append('dateTo', dateTo);
        } else if (currentTimeRange === 'custom') {
            if (customDateFrom) params.append('dateFrom', customDateFrom);
            if (customDateTo) params.append('dateTo', customDateTo);
        }

        // Add pagination parameters
        params.append('page', currentPage);
        params.append('size', pageSize);
        params.append('sort', currentSort);

        url += '?' + params.toString();

        const response = await Auth.fetch(url);

        if (response.status === 403) {
            alert('У вас нет прав для просмотра транзакций');
            return;
        }

        if (!response.ok) throw new Error('Failed to fetch transactions');

        const pagedResponse = await response.json();

        // Extract transactions from paginated response
        transactions = pagedResponse.content;
        totalPages = pagedResponse.totalPages;

        const tbody = document.querySelector('#transactionsTable tbody');
        tbody.innerHTML = '';

        transactions.forEach(tx => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="text-mono">${tx.id}</td>
                <td class="text-mono text-xs">${formatTimestamp(tx.timestamp)}</td>
                <td class="text-mono text-xs">${tx.sourceId}</td>
                <td class="text-mono text-xs">${tx.destinationId}</td>
                <td><strong>${tx.currency || 'USD'} ${tx.amount.toLocaleString()}</strong></td>
                <td>${getStatusBadge(tx.status)}</td>
                <td class="text-mono text-xs text-gray">
                    ${tx.correlationId}
                    <button class="btn-icon" onclick="copyToClipboard('${tx.correlationId}')" style="margin-left: 0.25rem;" title="Копировать">📋</button>
                </td>
                <td style="text-align: center;">
                    <button class="btn-icon btn-view" onclick="showTransactionDetails(${tx.id})">👁️</button>
                </td>
            `;
            tbody.appendChild(row);
        });

        // Update pagination controls
        updatePaginationControls(pagedResponse);
    } catch (error) {
        console.error('Error loading transactions:', error);
        alert('Ошибка загрузки транзакций: ' + error.message);
    }
}

// Get status badge HTML
function getStatusBadge(status) {
    const statusLower = status.toLowerCase();
    const badges = {
        processed: { class: 'badge-success', icon: '✓', label: 'Обработана' },
        alerted: { class: 'badge-danger', icon: '⚠', label: 'Помечена' },
        reviewed: { class: 'badge-purple', icon: '✓', label: 'Проверена' },
        pending: { class: 'badge-warning', icon: '⏳', label: 'Ожидает' },
    };

    const badge = badges[statusLower] || badges.processed;
    return `<span class="badge ${badge.class}">${badge.icon} ${badge.label}</span>`;
}

// Show transaction details
function showTransactionDetails(txId) {
    const tx = transactions.find(t => t.id === txId);
    if (!tx) return;

    const detailsPanel = document.getElementById('transactionDetails');
    const detailsContent = document.getElementById('detailsContent');

    detailsContent.innerHTML = `
        <div style="margin-bottom: 1rem;">
            <div class="text-sm text-gray">Correlation ID:</div>
            <div class="text-mono text-xs" style="margin-top: 0.25rem;">
                ${tx.correlationId}
                <button class="btn-icon" onclick="copyToClipboard('${tx.correlationId}')">📋</button>
            </div>
        </div>
        <div style="margin-bottom: 1rem;">
            <span class="text-sm text-gray">Сумма:</span> <strong>${tx.currency || 'USD'} ${tx.amount.toLocaleString()}</strong>
        </div>
        <div style="margin-bottom: 1rem;">
            <span class="text-sm text-gray">От/Кому:</span>
            <div class="text-mono text-xs">${tx.sourceId} → ${tx.destinationId}</div>
        </div>
        <div style="margin-bottom: 1rem;">
            <span class="text-sm text-gray">Статус:</span>
            <div style="margin-top: 0.25rem;">${getStatusBadge(tx.status)}</div>
        </div>
        <div style="margin-bottom: 1rem;">
            <span class="text-sm text-gray">Время:</span>
            <div class="text-mono text-xs">${formatTimestamp(tx.timestamp)}</div>
        </div>
        ${tx.triggeredRuleId ? `
        <div style="padding-top: 0.75rem; border-top: 1px solid var(--gray-200);">
            <div class="text-sm text-gray" style="font-weight: 600; margin-bottom: 0.5rem;">Сработало правило:</div>
            <div style="font-size: 0.75rem;">
                <strong>${tx.triggeredRuleName}</strong> (ID: ${tx.triggeredRuleId})
            </div>
            <div style="font-size: 0.75rem; color: var(--red-600); margin-top: 0.5rem;">
                ${tx.triggerReason || 'Нет описания'}
            </div>
            ${renderMLScoreDetails(tx.ruleMetadata)}
        </div>
        ` : '<p style="font-size: 0.75rem; color: var(--green-600); padding-top: 0.75rem; border-top: 1px solid var(--gray-200);">✓ Нет срабатываний</p>'}
    `;

    detailsPanel.style.display = 'block';

    // Adjust grid layout
    const contentGrid = document.querySelector('.content-grid');
    if (contentGrid) {
        contentGrid.style.gridTemplateColumns = '2fr 1fr';
    }
}

// Format timestamp
function formatTimestamp(timestamp) {
    if (Array.isArray(timestamp)) {
        // Format: [year, month, day, hour, minute, second, nano]
        const [year, month, day, hour, minute, second] = timestamp;
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`;
    }
    return timestamp;
}

// Render ML Score Details (for ML rules)
function renderMLScoreDetails(ruleMetadata) {
    if (!ruleMetadata || !ruleMetadata.ml_score) {
        return '';
    }

    const score = ruleMetadata.ml_score;
    const threshold = ruleMetadata.ml_threshold || 0.5;
    const modelVersion = ruleMetadata.ml_model_version || 'unknown';
    const scorePercent = (score * 100).toFixed(1);

    return `
        <div class="ml-score-section" style="margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid var(--gray-200);">
            <div class="text-sm text-gray" style="font-weight: 600; margin-bottom: 0.5rem;">ML Fraud Score:</div>

            <div style="margin-bottom: 0.75rem;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.25rem;">
                    <span class="text-sm" style="font-weight: 600;">${scorePercent}%</span>
                    <span class="text-xs text-gray">Threshold: ${(threshold * 100).toFixed(0)}%</span>
                </div>
                <div class="ml-score-meter" style="width: 100%; height: 8px; background-color: var(--gray-200); border-radius: 4px; overflow: hidden;">
                    <div style="width: ${scorePercent}%; height: 100%; background-color: var(--red-600); transition: width 0.3s ease;"></div>
                </div>
            </div>

            <div style="display: flex; gap: 0.5rem; align-items: center;">
                <span class="text-xs text-gray">Model:</span>
                <span class="badge" style="font-size: 0.65rem; padding: 0.125rem 0.375rem; background-color: var(--blue-100); color: var(--blue-700);">${modelVersion}</span>
            </div>
        </div>
    `;
}

// Close details
function closeDetails() {
    document.getElementById('transactionDetails').style.display = 'none';

    // Expand table to full width
    const contentGrid = document.querySelector('.content-grid');
    if (contentGrid) {
        contentGrid.style.gridTemplateColumns = '1fr';
    }
}

// Copy to clipboard
function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        alert('Скопировано в буфер обмена');
    });
}

// Update pagination controls
function updatePaginationControls(pagedResponse) {
    const paginationInfo = document.getElementById('paginationInfo');
    const prevBtn = document.getElementById('prevPage');
    const nextBtn = document.getElementById('nextPage');

    if (paginationInfo) {
        const start = pagedResponse.currentPage * pagedResponse.pageSize + 1;
        const end = Math.min(start + pagedResponse.pageSize - 1, pagedResponse.totalElements);
        paginationInfo.textContent = `Показано ${start}-${end} из ${pagedResponse.totalElements}`;
    }

    if (prevBtn) {
        prevBtn.disabled = pagedResponse.first;
    }

    if (nextBtn) {
        nextBtn.disabled = pagedResponse.last;
    }
}

// Navigate to previous page
function prevPage() {
    if (currentPage > 0) {
        currentPage--;
        loadTransactions();
    }
}

// Navigate to next page
function nextPage() {
    if (currentPage < totalPages - 1) {
        currentPage++;
        loadTransactions();
    }
}

// Change page size
function changePageSize(newSize) {
    pageSize = parseInt(newSize);
    currentPage = 0; // Reset to first page
    loadTransactions();
}

// Change sort order
function changeSortOrder(newSort) {
    currentSort = newSort;
    currentPage = 0; // Reset to first page
    loadTransactions();
}

// Apply time range filter
function applyTimeRange(value) {
    currentTimeRange = value;

    // Show/hide custom date range inputs
    const customDateRange = document.getElementById('customDateRange');
    if (value === 'custom') {
        customDateRange.style.display = 'flex';
    } else {
        customDateRange.style.display = 'none';
        customDateFrom = null;
        customDateTo = null;
    }

    currentPage = 0; // Reset to first page
    loadTransactions();
}

// Clear all filters
function clearFilters() {
    // Reset all select fields
    document.getElementById('filterStatus').value = 'all';
    document.getElementById('sortOrder').value = 'newest';
    document.getElementById('timeRange').value = 'all';

    // Clear text inputs
    document.getElementById('searchFrom').value = '';
    document.getElementById('searchTo').value = '';
    document.getElementById('searchCorrelation').value = '';

    // Clear date inputs
    document.getElementById('dateFrom').value = '';
    document.getElementById('dateTo').value = '';

    // Hide custom date range
    document.getElementById('customDateRange').style.display = 'none';

    // Reset state variables
    currentTimeRange = 'all';
    customDateFrom = null;
    customDateTo = null;
    currentSort = 'newest';
    currentPage = 0;

    // Reload transactions with default filters
    loadTransactions();
}

// Calculate time range for quick filters
function calculateTimeRange(range) {
    const now = new Date();
    let dateFrom, dateTo;

    switch (range) {
        case 'today':
            dateFrom = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
            dateTo = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59);
            break;
        case 'week':
            dateFrom = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
            dateTo = now;
            break;
        case 'month':
            dateFrom = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
            dateTo = now;
            break;
        default:
            return { dateFrom: null, dateTo: null };
    }

    // Format to ISO 8601 (YYYY-MM-DDTHH:mm:ss)
    const formatDate = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
    };

    return {
        dateFrom: formatDate(dateFrom),
        dateTo: formatDate(dateTo)
    };
}

// Helper function to parse date (supports both ISO string and array format from Java)
function parseDate(dateValue) {
    if (!dateValue) return null;

    // If it's an array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateValue)) {
        const [year, month, day, hour, minute, second] = dateValue;
        // JavaScript months are 0-indexed, Java months are 1-indexed
        return new Date(year, month - 1, day, hour || 0, minute || 0, second || 0);
    }

    // Otherwise try parsing as ISO string
    return new Date(dateValue);
}

// Load rules with metrics
// Render rule parameters display (for ML rules, show threshold and fallback)
function renderRuleParams(rule) {
    if (rule.ruleType !== 'ML') {
        return '';
    }

    try {
        const params = JSON.parse(rule.paramsJson);
        const parts = [];

        if (params.threshold !== undefined) {
            const thresholdPercent = (params.threshold * 100).toFixed(0);
            parts.push(`<span style="font-size: 0.7rem; color: var(--gray-600);">threshold=${thresholdPercent}%</span>`);
        }

        if (params.fallbackAction) {
            const color = params.fallbackAction === 'ALERTED' ? 'var(--red-600)' : 'var(--green-600)';
            parts.push(`<span style="font-size: 0.7rem; color: ${color};">fallback=${params.fallbackAction}</span>`);
        }

        if (parts.length > 0) {
            return '<br/><div style="margin-top: 0.25rem;">' + parts.join(' | ') + '</div>';
        }

        return '';
    } catch (e) {
        return '';
    }
}

async function loadRules() {
    try {
        // Load rules and metrics in parallel
        const [rulesResponse, metricsResponse] = await Promise.all([
            Auth.fetch(`${API_BASE}/rules`),
            Auth.fetch(`${API_BASE}/rules/metrics`)
        ]);

        if (rulesResponse.status === 403) {
            alert('У вас нет прав для просмотра правил');
            return;
        }

        if (!rulesResponse.ok) throw new Error('Failed to fetch rules');
        if (!metricsResponse.ok) throw new Error('Failed to fetch metrics');

        rules = await rulesResponse.json();
        const metrics = await metricsResponse.json();

        // Sort rules by priority (to keep consistent order)
        rules.sort((a, b) => a.priority - b.priority);

        // Create metrics map by ruleId for quick lookup
        const metricsMap = {};
        metrics.forEach(m => metricsMap[m.ruleId] = m);

        // Calculate statistics for cards
        const totalRules = rules.length;
        const enabledRules = rules.filter(r => r.enabled).length;
        const disabledRules = totalRules - enabledRules;
        const totalTriggers24h = metrics.reduce((sum, m) => sum + (m.triggersLast24h || 0), 0);

        // Update statistics cards
        document.getElementById('totalRules').textContent = totalRules;
        document.getElementById('enabledRules').textContent = enabledRules;
        document.getElementById('disabledRules').textContent = disabledRules;
        document.getElementById('totalTriggers24h').textContent = totalTriggers24h.toLocaleString();

        const tbody = document.querySelector('#rulesTable tbody');
        tbody.innerHTML = '';

        const isViewer = Auth.isViewer();

        rules.forEach(rule => {
            const row = document.createElement('tr');
            row.setAttribute('data-rule-id', rule.id); // Add ID for easy updates
            const ruleMetrics = metricsMap[rule.id] || {};

            // Enhanced status indicator with icon
            const statusIcon = rule.enabled ? '🟢' : '🔴';
            const statusText = rule.enabled ? 'Включено' : 'Выключено';
            const statusCell = isViewer
                ? `<span class="badge ${rule.enabled ? 'status-enabled' : 'status-disabled'}" title="${statusText}">${statusIcon} ${rule.enabled ? 'Вкл' : 'Выкл'}</span>`
                : `<button class="badge ${rule.enabled ? 'status-enabled' : 'status-disabled'}" onclick="toggleRule(${rule.id})" title="Переключить">${statusIcon} ${rule.enabled ? 'Вкл' : 'Выкл'}</button>`;

            // Metrics cells
            const totalTriggers = ruleMetrics.totalTriggers || 0;
            const triggers24h = ruleMetrics.triggersLast24h || 0;
            const triggers7d = ruleMetrics.triggersLast7d || 0;

            // Format last triggered time with proper date parsing
            let lastTriggered = '-';
            if (ruleMetrics.lastTriggered) {
                const date = parseDate(ruleMetrics.lastTriggered);
                if (date && !isNaN(date.getTime())) {
                    const now = new Date();
                    const diffMs = now - date;
                    const diffMins = Math.floor(diffMs / 60000);
                    const diffHours = Math.floor(diffMs / 3600000);
                    const diffDays = Math.floor(diffMs / 86400000);

                    if (diffMins < 1) {
                        lastTriggered = 'только что';
                    } else if (diffMins < 60) {
                        lastTriggered = `${diffMins} мин назад`;
                    } else if (diffHours < 24) {
                        lastTriggered = `${diffHours} ч назад`;
                    } else {
                        lastTriggered = `${diffDays} дн назад`;
                    }
                } else {
                    lastTriggered = 'ошибка даты';
                }
            }

            // Activity indicator based on recent triggers (moved to separate column)
            let activityBadge = '';
            if (triggers24h > 100) {
                activityBadge = '<span class="badge badge-danger" style="font-size: 0.7rem;">🔥 Высокая</span>';
            } else if (triggers24h > 10) {
                activityBadge = '<span class="badge badge-warning" style="font-size: 0.7rem;">📊 Средняя</span>';
            } else if (triggers24h > 0) {
                activityBadge = '<span class="badge badge-info" style="font-size: 0.7rem;">💤 Низкая</span>';
            } else {
                activityBadge = '<span class="badge badge-secondary" style="font-size: 0.7rem;">⚪ Нет</span>';
            }

            // Enhanced action buttons
            const actionsCell = isViewer
                ? `<button class="btn-icon btn-view" onclick="viewRuleDetails(${rule.id})" title="Просмотр">👁️</button>`
                : `<div class="btn-group">
                     <button class="btn-icon btn-edit" onclick="editRule(${rule.id})" title="Редактировать">✏️</button>
                     <button class="btn-icon btn-delete" onclick="deleteRule(${rule.id})" title="Удалить">🗑️</button>
                   </div>`;

            // Render rule parameters (for ML rules, show threshold and fallbackAction)
            const paramsDisplay = renderRuleParams(rule);

            row.innerHTML = `
                <td><strong>${rule.name}</strong></td>
                <td><span class="badge badge-info">${rule.ruleType}</span>${paramsDisplay}</td>
                <td style="text-align: center;"><strong>${rule.priority}</strong></td>
                <td>${statusCell}</td>
                <td style="text-align: center;">${activityBadge}</td>
                <td style="text-align: right;"><strong>${totalTriggers.toLocaleString()}</strong></td>
                <td style="text-align: right;"><span class="text-mono">${triggers24h} / ${triggers7d}</span></td>
                <td><span class="text-sm text-gray">${lastTriggered}</span></td>
                <td class="text-mono">${rule.version}</td>
                <td style="text-align: right;">${actionsCell}</td>
            `;
            tbody.appendChild(row);
        });

        // Update rule performance
        const perfContainer = document.getElementById('rulePerformance');
        if (perfContainer) {
            perfContainer.innerHTML = rules.map(rule => {
                const m = metricsMap[rule.id] || {};
                const triggers = m.triggersLast24h || 0;
                return `
                    <div class="metric-row">
                        <span>${rule.name}</span>
                        <span class="text-mono text-xs text-gray">${triggers} срабатываний</span>
                    </div>
                `;
            }).join('');
        }
    } catch (error) {
        console.error('Error loading rules:', error);
        alert('Ошибка загрузки правил: ' + error.message);
    }
}

// Toggle rule
async function toggleRule(ruleId) {
    try {
        const response = await Auth.fetch(`${API_BASE}/rules/${ruleId}/toggle`, {
            method: 'PATCH',
        });

        if (response.status === 403) {
            alert('У вас нет прав для изменения правил');
            return;
        }

        if (!response.ok) throw new Error('Failed to toggle rule');

        const updatedRule = await response.json();

        // Update local data
        const index = rules.findIndex(r => r.id === ruleId);
        if (index !== -1) {
            rules[index] = updatedRule;
        }

        // Update only the specific row in the table (without full reload)
        const row = document.querySelector(`tr[data-rule-id="${ruleId}"]`);
        if (row) {
            const statusIcon = updatedRule.enabled ? '🟢' : '🔴';
            const statusText = updatedRule.enabled ? 'Включено' : 'Выключено';
            const statusCell = row.querySelector('td:nth-child(4)'); // Status column

            if (statusCell) {
                statusCell.innerHTML = `<button class="badge ${updatedRule.enabled ? 'status-enabled' : 'status-disabled'}" onclick="toggleRule(${ruleId})" title="Переключить">${statusIcon} ${updatedRule.enabled ? 'Вкл' : 'Выкл'}</button>`;
            }
        }

        // Update statistics cards
        const enabledCount = rules.filter(r => r.enabled).length;
        const disabledCount = rules.length - enabledCount;
        document.getElementById('enabledRules').textContent = enabledCount;
        document.getElementById('disabledRules').textContent = disabledCount;

        loadAuditLog(); // Reload audit log to show the change
    } catch (error) {
        console.error('Error toggling rule:', error);
        alert('Ошибка переключения правила: ' + error.message);
    }
}

// Rule example templates
const RULE_EXAMPLES = {
    THRESHOLD: {
        'Large Amount (>10000)': {
            field: "amount",
            operator: ">",
            threshold: 10000
        },
        'Non-USD Currency': {
            field: "currency",
            operator: "!=",
            value: "USD"
        },
        'Night Transaction (hour >= 22)': {
            field: "hour",
            operator: ">=",
            threshold: 22
        },
        'High-risk Country': {
            field: "geo",
            operator: "==",
            value: "XX"
        }
    },
    PATTERN: {
        'Rapid Small Transfers': {
            patternType: "rapid_small_transfers",
            timeWindowMinutes: 10,
            minCount: 5,
            maxAmount: 500,
            groupBy: "from"
        },
        'Rapid Small Transfers (to same destination)': {
            patternType: "rapid_small_transfers",
            timeWindowMinutes: 5,
            minCount: 3,
            maxAmount: 150,
            groupBy: "to"
        }
    },
    COMPOSITE: {
        'Large Amount AND Night (AND logic)': {
            operator: "AND",
            conditions: [
                {
                    field: "amount",
                    operator: ">",
                    value: 5000
                },
                {
                    field: "hour",
                    operator: ">=",
                    value: 22
                }
            ]
        },
        'Very Large OR Non-USD (OR logic)': {
            operator: "OR",
            conditions: [
                {
                    field: "amount",
                    operator: ">",
                    value: 100000
                },
                {
                    field: "currency",
                    operator: "!=",
                    value: "USD"
                }
            ]
        }
    },
    ML: {
        'Production ML Rule (threshold=0.8, alert on error)': {
            threshold: 0.8,
            fallbackAction: "ALERTED"
        },
        'Lenient ML Rule (threshold=0.5, pass on error)': {
            threshold: 0.5,
            fallbackAction: "PASS"
        },
        'Strict ML Rule (threshold=0.9, pass on error)': {
            threshold: 0.9,
            fallbackAction: "PASS"
        }
    }
};

// Load rule example based on selected type
function loadRuleExample() {
    const ruleType = document.getElementById('ruleType').value;
    const examples = RULE_EXAMPLES[ruleType];

    if (!examples) {
        alert('Нет примеров для данного типа правила');
        return;
    }

    const exampleNames = Object.keys(examples);

    if (exampleNames.length === 1) {
        // Only one example, load it directly
        const exampleJson = examples[exampleNames[0]];
        document.getElementById('ruleParams').value = JSON.stringify(exampleJson, null, 2);
        validateRuleParams();
    } else {
        // Multiple examples, show selection
        let message = `Выберите пример для типа ${ruleType}:\n\n`;
        exampleNames.forEach((name, index) => {
            message += `${index + 1}. ${name}\n`;
        });

        const choice = prompt(message + '\nВведите номер примера (1-' + exampleNames.length + '):');
        const choiceIndex = parseInt(choice) - 1;

        if (choiceIndex >= 0 && choiceIndex < exampleNames.length) {
            const selectedName = exampleNames[choiceIndex];
            const exampleJson = examples[selectedName];
            document.getElementById('ruleParams').value = JSON.stringify(exampleJson, null, 2);
            validateRuleParams();
        }
    }
}

// Format rule params JSON
function formatRuleParams() {
    const textarea = document.getElementById('ruleParams');
    const value = textarea.value.trim();

    if (!value) {
        return;
    }

    try {
        const parsed = JSON.parse(value);
        textarea.value = JSON.stringify(parsed, null, 2);
        validateRuleParams();
    } catch (e) {
        alert('Ошибка форматирования: Некорректный JSON\n\n' + e.message);
    }
}

// Validate rule params JSON
function validateRuleParams() {
    const textarea = document.getElementById('ruleParams');
    const validationDiv = document.getElementById('paramsValidation');
    const ruleType = document.getElementById('ruleType').value;
    const value = textarea.value.trim();

    if (!value) {
        validationDiv.innerHTML = '';
        validationDiv.style.color = '';
        return true;
    }

    try {
        const parsed = JSON.parse(value);

        // ML-specific validation
        if (ruleType === 'ML') {
            const errors = [];

            // Validate threshold (must be 0-1)
            if (parsed.threshold !== undefined) {
                if (typeof parsed.threshold !== 'number') {
                    errors.push('threshold должен быть числом');
                } else if (parsed.threshold < 0 || parsed.threshold > 1) {
                    errors.push('threshold должен быть в диапазоне 0.0-1.0');
                }
            }

            // Validate fallbackAction (must be PASS or ALERTED)
            if (parsed.fallbackAction !== undefined) {
                if (parsed.fallbackAction !== 'PASS' && parsed.fallbackAction !== 'ALERTED') {
                    errors.push('fallbackAction должен быть "PASS" или "ALERTED"');
                }
            }

            if (errors.length > 0) {
                validationDiv.innerHTML = '<span style="color: var(--orange-600);">⚠ ' + errors.join('; ') + '</span>';
                return false;
            }
        }

        validationDiv.innerHTML = '<span style="color: var(--green-600);">✓ JSON корректен</span>';
        return true;
    } catch (e) {
        validationDiv.innerHTML = '<span style="color: var(--red-600);">✗ Ошибка JSON: ' + e.message + '</span>';
        return false;
    }
}

// Open rule editor
function openRuleEditor() {
    editingRule = null;
    document.getElementById('ruleEditor').style.display = 'block';
    document.getElementById('ruleForm').reset();
    document.getElementById('ruleParams').value = '{}';
    validateRuleParams();
}

// Edit rule
function editRule(ruleId) {
    const rule = rules.find(r => r.id === ruleId);
    if (!rule) return;

    editingRule = rule;
    document.getElementById('ruleEditor').style.display = 'block';
    document.getElementById('ruleName').value = rule.name;
    document.getElementById('ruleType').value = rule.ruleType;

    // Format and validate params
    try {
        const parsed = JSON.parse(rule.paramsJson);
        document.getElementById('ruleParams').value = JSON.stringify(parsed, null, 2);
    } catch (e) {
        document.getElementById('ruleParams').value = rule.paramsJson;
    }

    document.getElementById('rulePriority').value = rule.priority;
    validateRuleParams();
}

// Close rule editor
function closeRuleEditor() {
    document.getElementById('ruleEditor').style.display = 'none';
    editingRule = null;
}

// Delete rule
async function deleteRule(ruleId) {
    if (!confirm('Вы уверены, что хотите удалить это правило?')) {
        return;
    }

    try {
        const response = await Auth.fetch(`${API_BASE}/rules/${ruleId}`, {
            method: 'DELETE',
        });

        if (response.status === 403) {
            alert('У вас нет прав для удаления правил');
            return;
        }

        if (!response.ok) throw new Error('Failed to delete rule');

        // Remove from local data
        rules = rules.filter(r => r.id !== ruleId);
        loadRules();
        loadAuditLog(); // Reload audit log to show the change
    } catch (error) {
        console.error('Error deleting rule:', error);
        alert('Ошибка удаления правила: ' + error.message);
    }
}

// Handle rule form submission
document.addEventListener('DOMContentLoaded', function() {
    const ruleForm = document.getElementById('ruleForm');
    if (ruleForm) {
        ruleForm.addEventListener('submit', async function(e) {
            e.preventDefault();

            // Validate JSON before submission
            if (!validateRuleParams()) {
                alert('Пожалуйста, исправьте ошибки в JSON параметрах перед сохранением');
                return;
            }

            const ruleData = {
                name: document.getElementById('ruleName').value,
                ruleType: document.getElementById('ruleType').value,
                paramsJson: document.getElementById('ruleParams').value,
                priority: parseInt(document.getElementById('rulePriority').value),
                enabled: true,
            };

            try {
                let response;

                if (editingRule) {
                    // Update existing rule
                    response = await Auth.fetch(`${API_BASE}/rules/${editingRule.id}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(ruleData),
                    });

                    if (response.status === 403) {
                        alert('У вас нет прав для изменения правил');
                        return;
                    }

                    if (!response.ok) throw new Error('Failed to update rule');

                    const updatedRule = await response.json();
                    const index = rules.findIndex(r => r.id === editingRule.id);
                    if (index !== -1) {
                        rules[index] = updatedRule;
                    }
                } else {
                    // Create new rule
                    response = await Auth.fetch(`${API_BASE}/rules`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(ruleData),
                    });

                    if (response.status === 403) {
                        alert('У вас нет прав для создания правил');
                        return;
                    }

                    if (!response.ok) throw new Error('Failed to create rule');

                    const newRule = await response.json();
                    rules.push(newRule);
                }

                loadRules();
                loadAuditLog(); // Reload audit log to show the change
                closeRuleEditor();
            } catch (error) {
                console.error('Error saving rule:', error);
                alert('Ошибка сохранения правила: ' + error.message);
            }
        });
    }
});

// Load audit log
async function loadAuditLog() {
    try {
        const username = document.getElementById('filterAuditUsername')?.value || '';
        const action = document.getElementById('filterAuditAction')?.value || '';
        const ruleId = document.getElementById('filterAuditRuleId')?.value || '';
        const dateFrom = document.getElementById('auditDateFrom')?.value || '';
        const dateTo = document.getElementById('auditDateTo')?.value || '';
        const sort = document.getElementById('auditSortOrder')?.value || 'newest';

        const params = new URLSearchParams();
        if (username) params.append('username', username);
        if (action) params.append('action', action);
        if (ruleId) params.append('ruleId', ruleId);
        if (dateFrom) params.append('dateFrom', dateFrom);
        if (dateTo) params.append('dateTo', dateTo);
        params.append('page', auditCurrentPage);
        params.append('size', auditPageSize);
        params.append('sort', sort);

        const response = await Auth.fetch(`${API_BASE}/audit-logs?${params.toString()}`);

        if (response.status === 403) {
            console.warn('No access to audit logs');
            return;
        }

        if (!response.ok) throw new Error('Failed to fetch audit logs');

        const pagedResponse = await response.json();
        auditLog = pagedResponse.content;
        auditTotalPages = pagedResponse.totalPages;

        const tbody = document.querySelector('#auditTable tbody');
        tbody.innerHTML = '';

        if (auditLog.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: #6c757d;">Нет записей в журнале аудита</td></tr>';
            updateAuditPaginationInfo(0, 0, 0);
            renderAuditPagination(pagedResponse);
            return;
        }

        auditLog.forEach(entry => {
            const row = document.createElement('tr');
            const actionClass = entry.action === 'CREATE' ? 'badge-success' :
                               entry.action === 'DELETE' ? 'badge-danger' :
                               entry.action === 'ENABLE' ? 'badge-success' :
                               entry.action === 'DISABLE' ? 'badge-warning' : 'badge-info';

            row.innerHTML = `
                <td class="text-mono text-xs text-gray">${formatTimestamp(entry.timestamp)}</td>
                <td>${entry.username}</td>
                <td><span class="badge ${actionClass}">${entry.action}</span></td>
                <td class="text-mono">${entry.ruleId || '-'}</td>
                <td class="text-gray">${entry.details || '-'}</td>
            `;
            tbody.appendChild(row);
        });

        // Update pagination info
        const from = pagedResponse.currentPage * pagedResponse.pageSize + 1;
        const to = Math.min((pagedResponse.currentPage + 1) * pagedResponse.pageSize, pagedResponse.totalElements);
        updateAuditPaginationInfo(from, to, pagedResponse.totalElements);

        // Render pagination buttons
        renderAuditPagination(pagedResponse);
    } catch (error) {
        console.error('Error loading audit logs:', error);
    }
}

// Apply audit filters
function applyAuditFilters() {
    auditCurrentPage = 0;
    loadAuditLog();
}

// Clear audit filters
function clearAuditFilters() {
    document.getElementById('filterAuditUsername').value = '';
    document.getElementById('filterAuditAction').value = '';
    document.getElementById('filterAuditRuleId').value = '';
    document.getElementById('auditDateFrom').value = '';
    document.getElementById('auditDateTo').value = '';
    document.getElementById('auditSortOrder').value = 'newest';
    auditCurrentPage = 0;
    loadAuditLog();
}

// Update audit pagination info
function updateAuditPaginationInfo(from, to, total) {
    document.getElementById('auditShowing').textContent = `${from}-${to}`;
    document.getElementById('auditTotal').textContent = total;
}

// Render audit pagination buttons
function renderAuditPagination(data) {
    const container = document.getElementById('auditPagination');

    if (data.totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = '';

    // Previous button
    if (!data.first) {
        html += `<button class="btn btn-secondary" onclick="goToAuditPage(${data.currentPage - 1})">← Пред</button>`;
    }

    // Page numbers
    const maxButtons = 5;
    let startPage = Math.max(0, data.currentPage - Math.floor(maxButtons / 2));
    let endPage = Math.min(data.totalPages - 1, startPage + maxButtons - 1);

    if (endPage - startPage < maxButtons - 1) {
        startPage = Math.max(0, endPage - maxButtons + 1);
    }

    if (startPage > 0) {
        html += `<button class="btn btn-secondary" onclick="goToAuditPage(0)">1</button>`;
        if (startPage > 1) html += `<span style="padding: 0 0.5rem;">...</span>`;
    }

    for (let i = startPage; i <= endPage; i++) {
        const isActive = i === data.currentPage ? 'btn-primary' : 'btn-secondary';
        html += `<button class="btn ${isActive}" onclick="goToAuditPage(${i})">${i + 1}</button>`;
    }

    if (endPage < data.totalPages - 1) {
        if (endPage < data.totalPages - 2) html += `<span style="padding: 0 0.5rem;">...</span>`;
        html += `<button class="btn btn-secondary" onclick="goToAuditPage(${data.totalPages - 1}))">${data.totalPages}</button>`;
    }

    // Next button
    if (!data.last) {
        html += `<button class="btn btn-secondary" onclick="goToAuditPage(${data.currentPage + 1})">След →</button>`;
    }

    container.innerHTML = html;
}

// Go to specific audit page
function goToAuditPage(page) {
    auditCurrentPage = page;
    loadAuditLog();
}

// Load notifications
function loadNotifications() {
    // Load channels
    const channelsGrid = document.getElementById('channelsGrid');
    if (channelsGrid) {
        channelsGrid.innerHTML = notificationChannels.map(ch => `
            <div class="channel-card">
                <div class="channel-header">
                    <h3 class="channel-name">${ch.name}</h3>
                    <button class="badge ${ch.enabled ? 'status-enabled' : 'status-disabled'}"
                            onclick="toggleChannel(${ch.id})">
                        ${ch.enabled ? 'Вкл' : 'Выкл'}
                    </button>
                </div>
                <div class="channel-stats">
                    <div class="channel-stat">
                        <span>Отправлено:</span>
                        <span style="font-weight: bold; color: var(--green-600);">${ch.sent}</span>
                    </div>
                    <div class="channel-stat">
                        <span>Ошибок:</span>
                        <span style="font-weight: bold; color: var(--red-600);">${ch.failed}</span>
                    </div>
                    <div class="channel-stat">
                        <span>Повтор:</span>
                        <span style="font-weight: bold; color: var(--yellow-600);">${ch.retrying}</span>
                    </div>
                    <div class="channel-stat">
                        <span>Латенция:</span>
                        <span style="font-weight: bold;">${ch.avgLatency}ms</span>
                    </div>
                </div>
            </div>
        `).join('');
    }

    // Load notifications table
    const tbody = document.querySelector('#notificationsTable tbody');
    if (tbody) {
        tbody.innerHTML = '';

        notifications.forEach(notif => {
            const row = document.createElement('tr');
            const statusClass = notif.status === 'SENT' ? 'badge-success' :
                               notif.status === 'RETRYING' ? 'badge-warning' : 'badge-danger';
            const statusIcon = notif.status === 'SENT' ? '✓' :
                              notif.status === 'RETRYING' ? '⟳' : '✗';

            row.innerHTML = `
                <td class="text-mono text-xs" style="color: var(--blue-600);">${notif.id}</td>
                <td class="text-mono text-xs">${notif.correlationId}</td>
                <td>${notif.channel}</td>
                <td><span class="badge ${statusClass}">${statusIcon} ${notif.status}</span></td>
                <td class="text-mono text-xs">${notif.attempt}</td>
                <td class="text-mono text-xs">${notif.duration ? notif.duration + 'ms' : '—'}</td>
            `;
            tbody.appendChild(row);
        });
    }
}

// Toggle channel
function toggleChannel(channelId) {
    const channel = notificationChannels.find(c => c.id === channelId);
    if (channel) {
        channel.enabled = !channel.enabled;
        loadNotifications();
    }
}


// Export CSV
function exportCSV(type) {
    let csv, filename;

    if (type === 'transactions') {
        csv = 'ID,Валюта,Сумма,От,Кому,Статус,Время,Correlation ID\n';
        csv += transactions.map(t =>
            `${t.id},${t.currency || 'USD'},${t.amount},${t.sourceId},${t.destinationId},${t.status},${formatTimestamp(t.timestamp)},${t.correlationId}`
        ).join('\n');
        filename = 'transactions.csv';
    } else if (type === 'audit') {
        csv = 'Время,Пользователь,Действие,Правило ID,Детали\n';
        csv += auditLog.map(a =>
            `${a.timestamp},${a.user},${a.action},${a.ruleId || ''},${a.details}`
        ).join('\n');
        filename = 'audit_log.csv';
    }

    // Add BOM for UTF-8 encoding to fix cyrillic characters
    const BOM = '\uFEFF';
    const blob = new Blob([BOM + csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
}

// View rule details (for viewers)
function viewRuleDetails(ruleId) {
    const rule = rules.find(r => r.id === ruleId);
    if (!rule) {
        alert('Правило не найдено');
        return;
    }

    // Format JSON for better readability
    let formattedParams = rule.paramsJson;
    try {
        const parsed = JSON.parse(rule.paramsJson);
        formattedParams = JSON.stringify(parsed, null, 2);
    } catch (e) {
        // If not valid JSON, use as is
    }

    const details = `
╔════════════════════════════════════════════╗
║          ДЕТАЛИ ПРАВИЛА                   ║
╚════════════════════════════════════════════╝

📋 Название: ${rule.name}

🔧 Тип: ${rule.ruleType}

📊 Приоритет: ${rule.priority}

🔢 Версия: ${rule.version}

⚡ Статус: ${rule.enabled ? '✓ Включено' : '✗ Выключено'}

📝 Параметры:
${formattedParams}

📅 Создано: ${rule.createdAt ? new Date(rule.createdAt).toLocaleString('ru-RU') : 'N/A'}
📅 Обновлено: ${rule.updatedAt ? new Date(rule.updatedAt).toLocaleString('ru-RU') : 'N/A'}
    `.trim();

    alert(details);
}

// ============================================
// WEBSOCKET INTEGRATION FOR REAL-TIME ALERTS
// ============================================

let ws = null;
let wsReconnectTimer = null;

// Initialize WebSocket connection
function initWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/api/ws/alerts`;

    console.log('Connecting to WebSocket:', wsUrl);

    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        console.log('WebSocket connected');
        updateConnectionStatus(true);
        clearReconnectTimer();
    };

    ws.onmessage = function(event) {
        console.log('WebSocket message received:', event.data);
        try {
            const alert = JSON.parse(event.data);
            handleRealtimeAlert(alert);
        } catch (e) {
            console.error('Failed to parse WebSocket message:', e);
        }
    };

    ws.onerror = function(error) {
        console.error('WebSocket error:', error);
        updateConnectionStatus(false);
    };

    ws.onclose = function() {
        console.log('WebSocket disconnected');
        updateConnectionStatus(false);
        scheduleReconnect();
    };
}

// Handle real-time alert from WebSocket
function handleRealtimeAlert(alert) {
    console.log('Real-time alert received:', alert);

    // Show browser notification
    if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('🚨 Подозрительная транзакция', {
            body: `${alert.ruleName}: ${alert.reason}`,
            icon: '/favicon.ico'
        });
    }

    // Reload data if on relevant tab
    const activeTab = document.querySelector('.tab-content.active');
    if (activeTab) {
        const tabId = activeTab.id;
        if (tabId === 'transactions') {
            loadTransactions();
        } else if (tabId === 'notifications') {
            loadNotifications();
        }
    }
}

// Update connection status indicator
function updateConnectionStatus(connected) {
    const indicator = document.getElementById('wsStatus');
    if (indicator) {
        indicator.textContent = connected ? '🟢 Connected' : '🔴 Disconnected';
        indicator.className = connected ? 'ws-status connected' : 'ws-status disconnected';
    }
}

// Schedule WebSocket reconnection
function scheduleReconnect() {
    clearReconnectTimer();
    wsReconnectTimer = setTimeout(function() {
        console.log('Attempting to reconnect WebSocket...');
        initWebSocket();
    }, 5000); // 5 seconds
}

// Clear reconnect timer
function clearReconnectTimer() {
    if (wsReconnectTimer) {
        clearTimeout(wsReconnectTimer);
        wsReconnectTimer = null;
    }
}

// Request notification permissions on load
if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission();
}

// Initialize WebSocket when authenticated
if (typeof Auth !== 'undefined' && Auth.isAuthenticated && Auth.isAuthenticated()) {
    initWebSocket();
}
