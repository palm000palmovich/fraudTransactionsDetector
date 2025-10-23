// API Configuration
const API_BASE = '/api/admin';

// Data
let transactions = [];
let rules = [];
let auditLog = [];
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
    // Check if user is logged in
    const userStr = sessionStorage.getItem('user');
    if (!userStr) {
        window.location.href = 'login.html';
        return;
    }

    currentUser = JSON.parse(userStr);
    document.getElementById('userEmail').textContent = currentUser.email;

    // Initialize tabs
    initializeTabs();

    // Load initial data
    loadTransactions();
    loadRules();
    loadStats();
    loadAuditLog();
    loadNotifications();
    loadCharts();

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
});

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
    sessionStorage.removeItem('user');
    window.location.href = 'login.html';
}

// Load statistics
async function loadStats() {
    try {
        const response = await fetch(`${API_BASE}/transactions/stats`);
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

        const response = await fetch(url);
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

// Load rules
async function loadRules() {
    try {
        const response = await fetch(`${API_BASE}/rules`);
        if (!response.ok) throw new Error('Failed to fetch rules');

        rules = await response.json();

        const tbody = document.querySelector('#rulesTable tbody');
        tbody.innerHTML = '';

        rules.forEach(rule => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>${rule.name}</strong></td>
                <td><span class="badge badge-info">${rule.ruleType}</span></td>
                <td>${rule.priority}</td>
                <td>
                    <button class="badge ${rule.enabled ? 'status-enabled' : 'status-disabled'}" onclick="toggleRule(${rule.id})">
                        ${rule.enabled ? '✓ Вкл' : '✗ Выкл'}
                    </button>
                </td>
                <td class="text-mono">${rule.version}</td>
                <td style="text-align: right;">
                    <button class="btn-icon btn-edit" onclick="editRule(${rule.id})">✏️</button>
                    <button class="btn-icon btn-delete" onclick="deleteRule(${rule.id})">🗑️</button>
                </td>
            `;
            tbody.appendChild(row);
        });

        // Update rule performance
        const perfContainer = document.getElementById('rulePerformance');
        if (perfContainer) {
            perfContainer.innerHTML = rules.map(rule => `
                <div class="metric-row">
                    <span>${rule.name}</span>
                    <span class="text-mono text-xs text-gray">~2-5ms</span>
                </div>
            `).join('');
        }
    } catch (error) {
        console.error('Error loading rules:', error);
        alert('Ошибка загрузки правил: ' + error.message);
    }
}

// Toggle rule
async function toggleRule(ruleId) {
    try {
        const response = await fetch(`${API_BASE}/rules/${ruleId}/toggle`, {
            method: 'PATCH',
        });

        if (!response.ok) throw new Error('Failed to toggle rule');

        const updatedRule = await response.json();

        // Update local data
        const index = rules.findIndex(r => r.id === ruleId);
        if (index !== -1) {
            rules[index] = updatedRule;
        }

        loadRules();
        addAuditLog(updatedRule.enabled ? 'ENABLE' : 'DISABLE', ruleId, `Правило ${updatedRule.enabled ? 'включено' : 'отключено'}`);
    } catch (error) {
        console.error('Error toggling rule:', error);
        alert('Ошибка переключения правила: ' + error.message);
    }
}

// Open rule editor
function openRuleEditor() {
    editingRule = null;
    document.getElementById('ruleEditor').style.display = 'block';
    document.getElementById('ruleForm').reset();
    document.getElementById('ruleParams').value = '{}';
}

// Edit rule
function editRule(ruleId) {
    const rule = rules.find(r => r.id === ruleId);
    if (!rule) return;

    editingRule = rule;
    document.getElementById('ruleEditor').style.display = 'block';
    document.getElementById('ruleName').value = rule.name;
    document.getElementById('ruleType').value = rule.ruleType;
    document.getElementById('ruleParams').value = rule.paramsJson;
    document.getElementById('rulePriority').value = rule.priority;
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
        const response = await fetch(`${API_BASE}/rules/${ruleId}`, {
            method: 'DELETE',
        });

        if (!response.ok) throw new Error('Failed to delete rule');

        // Remove from local data
        rules = rules.filter(r => r.id !== ruleId);
        loadRules();
        addAuditLog('DELETE', ruleId, 'Правило удалено');
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
                    response = await fetch(`${API_BASE}/rules/${editingRule.id}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(ruleData),
                    });

                    if (!response.ok) throw new Error('Failed to update rule');

                    const updatedRule = await response.json();
                    const index = rules.findIndex(r => r.id === editingRule.id);
                    if (index !== -1) {
                        rules[index] = updatedRule;
                    }
                    addAuditLog('UPDATE', updatedRule.id, 'Правило обновлено');
                } else {
                    // Create new rule
                    response = await fetch(`${API_BASE}/rules`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(ruleData),
                    });

                    if (!response.ok) throw new Error('Failed to create rule');

                    const newRule = await response.json();
                    rules.push(newRule);
                    addAuditLog('CREATE', newRule.id, 'Создано новое правило');
                }

                loadRules();
                closeRuleEditor();
            } catch (error) {
                console.error('Error saving rule:', error);
                alert('Ошибка сохранения правила: ' + error.message);
            }
        });
    }
});

// Load audit log
function loadAuditLog() {
    const tbody = document.querySelector('#auditTable tbody');
    tbody.innerHTML = '';

    auditLog.forEach(entry => {
        const row = document.createElement('tr');
        const actionClass = entry.action === 'CREATE' ? 'badge-success' :
                           entry.action === 'DELETE' ? 'badge-danger' : 'badge-info';

        row.innerHTML = `
            <td class="text-mono text-xs text-gray">${entry.timestamp}</td>
            <td>${entry.user}</td>
            <td><span class="badge ${actionClass}">${entry.action}</span></td>
            <td class="text-mono">${entry.ruleId || '-'}</td>
            <td class="text-gray">${entry.details}</td>
        `;
        tbody.appendChild(row);
    });
}

// Add audit log entry
function addAuditLog(action, ruleId, details) {
    const entry = {
        id: auditLog.length + 1,
        timestamp: new Date().toLocaleString('ru-RU'),
        user: currentUser.email,
        action: action,
        ruleId: ruleId,
        details: details,
    };
    auditLog.unshift(entry);
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

// Load charts
function loadCharts() {
    const metricsData = [
        { date: 'Пн', processed: 240, alerted: 85, reviewed: 120 },
        { date: 'Вт', processed: 320, alerted: 92, reviewed: 180 },
        { date: 'Ср', processed: 280, alerted: 78, reviewed: 150 },
        { date: 'Чт', processed: 410, alerted: 110, reviewed: 240 },
        { date: 'Пт', processed: 520, alerted: 165, reviewed: 380 },
    ];

    // Line chart
    const lineCtx = document.getElementById('lineChart');
    if (lineCtx) {
        new Chart(lineCtx, {
            type: 'line',
            data: {
                labels: metricsData.map(d => d.date),
                datasets: [
                    {
                        label: 'Обработано',
                        data: metricsData.map(d => d.processed),
                        borderColor: '#3b82f6',
                        backgroundColor: 'rgba(59, 130, 246, 0.1)',
                        tension: 0.3,
                    },
                    {
                        label: 'Помечено',
                        data: metricsData.map(d => d.alerted),
                        borderColor: '#ef4444',
                        backgroundColor: 'rgba(239, 68, 68, 0.1)',
                        tension: 0.3,
                    },
                    {
                        label: 'Проверено',
                        data: metricsData.map(d => d.reviewed),
                        borderColor: '#10b981',
                        backgroundColor: 'rgba(16, 185, 129, 0.1)',
                        tension: 0.3,
                    },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'bottom',
                    }
                }
            }
        });
    }

    // Bar chart
    const barCtx = document.getElementById('barChart');
    if (barCtx) {
        new Chart(barCtx, {
            type: 'bar',
            data: {
                labels: metricsData.map(d => d.date),
                datasets: [
                    {
                        label: 'Обработано',
                        data: metricsData.map(d => d.processed),
                        backgroundColor: '#3b82f6',
                    },
                    {
                        label: 'Помечено',
                        data: metricsData.map(d => d.alerted),
                        backgroundColor: '#ef4444',
                    },
                    {
                        label: 'Проверено',
                        data: metricsData.map(d => d.reviewed),
                        backgroundColor: '#10b981',
                    },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        position: 'bottom',
                    }
                }
            }
        });
    }
}

// Export CSV
function exportCSV(type) {
    let csv, filename;

    if (type === 'metrics') {
        csv = 'Дата,Обработано,Помечено,Проверено\n';
        csv += 'Пн,240,85,120\n';
        csv += 'Вт,320,92,180\n';
        csv += 'Ср,280,78,150\n';
        csv += 'Чт,410,110,240\n';
        csv += 'Пт,520,165,380\n';
        filename = 'metrics.csv';
    } else if (type === 'transactions') {
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
