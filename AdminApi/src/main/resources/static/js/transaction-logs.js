/**
 * Transaction Logs Module
 * Handles timeline and table views for transaction lifecycle logs
 */

const TransactionLogs = {
    currentPage: 0,
    pageSize: 50,
    lastFilters: null,

    /**
     * Initialize the module
     */
    init() {
        console.log('Transaction Logs module initialized');
        // Set default date range (last 7 days)
        this.setDefaultDateRange();
    },

    /**
     * Set default date range
     */
    setDefaultDateRange() {
        const now = new Date();
        const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);

        document.getElementById('logsDateFrom').value = this.formatDateTimeLocal(weekAgo);
        document.getElementById('logsDateTo').value = this.formatDateTimeLocal(now);
    },

    /**
     * Format date for datetime-local input
     */
    formatDateTimeLocal(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    },

    /**
     * Collect current filter values
     */
    collectFilters() {
        const correlationId = document.getElementById('searchCorrelationId').value.trim();
        const level = document.getElementById('filterLevel').value;
        const component = document.getElementById('filterComponent').value;
        const dateFrom = document.getElementById('logsDateFrom').value;
        const dateTo = document.getElementById('logsDateTo').value;
        const sort = document.getElementById('logsSortOrder').value;

        return {
            correlationId: correlationId || null,
            level: level || null,
            component: component || null,
            dateFrom: dateFrom || null,
            dateTo: dateTo || null,
            sort: sort || 'newest',
            page: this.currentPage,
            size: this.pageSize
        };
    },

    /**
     * Search logs with current filters
     */
    async search() {
        const filters = this.collectFilters();
        this.lastFilters = filters;
        this.currentPage = 0;
        filters.page = 0;

        try {
            const data = await this.fetchLogs(filters);

            if (filters.correlationId) {
                // Show timeline view for single transaction
                this.showTimelineView(data.content, filters.correlationId);
            } else {
                // Show table view for multiple logs
                this.showTableView(data);
            }
        } catch (error) {
            console.error('Error fetching logs:', error);
            alert('Ошибка при загрузке логов: ' + error.message);
        }
    },

    /**
     * Fetch logs from API
     */
    async fetchLogs(filters) {
        const params = new URLSearchParams();

        if (filters.correlationId) params.append('correlationId', filters.correlationId);
        if (filters.level) params.append('level', filters.level);
        if (filters.component) params.append('component', filters.component);
        if (filters.dateFrom) params.append('dateFrom', filters.dateFrom);
        if (filters.dateTo) params.append('dateTo', filters.dateTo);
        if (filters.sort) params.append('sort', filters.sort);
        params.append('page', filters.page);
        params.append('size', filters.size);

        const response = await Auth.fetch(`/api/transaction-logs?${params.toString()}`);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        return await response.json();
    },

    /**
     * Show Timeline View for single transaction
     */
    showTimelineView(logs, correlationId) {
        // Hide table, show timeline
        document.getElementById('tableView').style.display = 'none';
        document.getElementById('timelineView').style.display = 'block';

        // Set correlation ID
        document.getElementById('timelineCorrelationId').textContent = correlationId;

        // Render timeline
        this.renderTimeline(logs);
    },

    /**
     * Show Table View for multiple logs
     */
    showTableView(data) {
        // Hide timeline, show table
        document.getElementById('timelineView').style.display = 'none';
        document.getElementById('tableView').style.display = 'block';

        // Render table
        this.renderTable(data);
    },

    /**
     * Switch from Timeline to Table view
     */
    switchToTableView() {
        // Clear correlation ID filter
        document.getElementById('searchCorrelationId').value = '';
        this.search();
    },

    /**
     * Render Timeline items
     */
    renderTimeline(logs) {
        const container = document.getElementById('timelineContainer');

        if (!logs || logs.length === 0) {
            container.innerHTML = '<div style="text-align: center; padding: 2rem; color: #6c757d;">Логи не найдены</div>';
            return;
        }

        let html = '';
        logs.forEach((log, index) => {
            const levelClass = log.level.toLowerCase();
            const isLast = index === logs.length - 1;

            html += `
                <div class="timeline-item ${levelClass}">
                    <div class="timeline-dot"></div>
                    <div class="timeline-content">
                        <div class="timeline-header">
                            <span class="timeline-time">${this.formatTimestamp(log.timestamp)}</span>
                            <span class="badge badge-${levelClass}">${log.level}</span>
                            <span class="badge badge-component">${log.component}</span>
                        </div>
                        <div class="timeline-message">${this.escapeHtml(log.message)}</div>
                        ${log.details ? `
                            <div class="timeline-details">
                                <details>
                                    <summary>Детали</summary>
                                    <pre>${this.formatJSON(log.details)}</pre>
                                </details>
                            </div>
                        ` : ''}
                    </div>
                </div>
            `;
        });

        container.innerHTML = html;
    },

    /**
     * Render Table with logs
     */
    renderTable(data) {
        const tbody = document.getElementById('transactionLogsTableBody');

        if (!data.content || data.content.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" style="text-align: center; padding: 2rem; color: #6c757d;">
                        Логи не найдены. Попробуйте изменить фильтры.
                    </td>
                </tr>
            `;
            this.updatePaginationInfo(0, 0, 0);
            this.renderPagination(data);
            return;
        }

        let html = '';
        data.content.forEach(log => {
            const levelClass = log.level.toLowerCase();
            const shortCorrelationId = log.correlationId.substring(0, 8) + '...';

            html += `
                <tr>
                    <td>${this.formatTimestamp(log.timestamp)}</td>
                    <td>
                        <a href="#" onclick="TransactionLogs.searchByCorrelationId('${log.correlationId}'); return false;"
                           title="${log.correlationId}"
                           style="text-decoration: none; color: #007bff;">
                            ${shortCorrelationId}
                        </a>
                    </td>
                    <td><span class="badge badge-${levelClass}">${log.level}</span></td>
                    <td><span class="badge badge-component">${log.component}</span></td>
                    <td>${this.escapeHtml(log.message)}</td>
                    <td>
                        ${log.details ? `
                            <button class="btn-small" onclick="TransactionLogs.showDetails(${log.id}, \`${this.escapeHtml(log.details)}\`)">
                                Показать
                            </button>
                        ` : '-'}
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;

        // Update pagination info
        const from = data.currentPage * data.pageSize + 1;
        const to = Math.min((data.currentPage + 1) * data.pageSize, data.totalElements);
        this.updatePaginationInfo(from, to, data.totalElements);

        // Render pagination buttons
        this.renderPagination(data);
    },

    /**
     * Search by correlation ID (from table click)
     */
    searchByCorrelationId(correlationId) {
        document.getElementById('searchCorrelationId').value = correlationId;
        this.search();
    },

    /**
     * Show details modal
     */
    showDetails(logId, details) {
        try {
            const formatted = this.formatJSON(details);
            alert(`Детали лога #${logId}:\n\n${formatted}`);
        } catch (e) {
            alert(`Детали лога #${logId}:\n\n${details}`);
        }
    },

    /**
     * Update pagination info text
     */
    updatePaginationInfo(from, to, total) {
        document.getElementById('logsShowing').textContent = `${from}-${to}`;
        document.getElementById('logsTotal').textContent = total;
    },

    /**
     * Render pagination buttons
     */
    renderPagination(data) {
        const container = document.getElementById('logsPagination');

        if (data.totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';

        // Previous button
        if (!data.first) {
            html += `<button class="btn btn-secondary" onclick="TransactionLogs.goToPage(${data.currentPage - 1})">← Пред</button>`;
        }

        // Page numbers
        const maxButtons = 5;
        let startPage = Math.max(0, data.currentPage - Math.floor(maxButtons / 2));
        let endPage = Math.min(data.totalPages - 1, startPage + maxButtons - 1);

        if (endPage - startPage < maxButtons - 1) {
            startPage = Math.max(0, endPage - maxButtons + 1);
        }

        if (startPage > 0) {
            html += `<button class="btn btn-secondary" onclick="TransactionLogs.goToPage(0)">1</button>`;
            if (startPage > 1) html += `<span style="padding: 0 0.5rem;">...</span>`;
        }

        for (let i = startPage; i <= endPage; i++) {
            const isActive = i === data.currentPage ? 'btn-primary' : 'btn-secondary';
            html += `<button class="btn ${isActive}" onclick="TransactionLogs.goToPage(${i})">${i + 1}</button>`;
        }

        if (endPage < data.totalPages - 1) {
            if (endPage < data.totalPages - 2) html += `<span style="padding: 0 0.5rem;">...</span>`;
            html += `<button class="btn btn-secondary" onclick="TransactionLogs.goToPage(${data.totalPages - 1})">${data.totalPages}</button>`;
        }

        // Next button
        if (!data.last) {
            html += `<button class="btn btn-secondary" onclick="TransactionLogs.goToPage(${data.currentPage + 1})">След →</button>`;
        }

        container.innerHTML = html;
    },

    /**
     * Go to specific page
     */
    async goToPage(page) {
        this.currentPage = page;
        if (this.lastFilters) {
            this.lastFilters.page = page;
            const data = await this.fetchLogs(this.lastFilters);
            this.showTableView(data);
        }
    },

    /**
     * Clear all filters
     */
    clearFilters() {
        document.getElementById('searchCorrelationId').value = '';
        document.getElementById('filterLevel').value = '';
        document.getElementById('filterComponent').value = '';
        document.getElementById('logsSortOrder').value = 'newest';
        this.setDefaultDateRange();
        this.currentPage = 0;

        // Clear results
        document.getElementById('transactionLogsTableBody').innerHTML = `
            <tr>
                <td colspan="6" style="text-align: center; padding: 2rem; color: #6c757d;">
                    Используйте фильтры для поиска логов транзакций
                </td>
            </tr>
        `;
        document.getElementById('logsPagination').innerHTML = '';
        this.updatePaginationInfo(0, 0, 0);
    },

    /**
     * Export to CSV
     */
    async exportCSV() {
        if (!this.lastFilters) {
            alert('Сначала выполните поиск');
            return;
        }

        try {
            // Fetch all logs without pagination
            const filters = {...this.lastFilters, page: 0, size: 10000};
            const data = await this.fetchLogs(filters);

            // Create CSV content
            let csv = 'Timestamp,Correlation ID,Level,Component,Message,Details\n';
            data.content.forEach(log => {
                const row = [
                    log.timestamp,
                    log.correlationId,
                    log.level,
                    log.component,
                    `"${log.message.replace(/"/g, '""')}"`,
                    log.details ? `"${log.details.replace(/"/g, '""')}"` : ''
                ].join(',');
                csv += row + '\n';
            });

            // Download
            const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = `transaction-logs-${new Date().toISOString().slice(0, 10)}.csv`;
            link.click();
        } catch (error) {
            console.error('Error exporting CSV:', error);
            alert('Ошибка при экспорте CSV: ' + error.message);
        }
    },

    /**
     * Format timestamp for display
     */
    formatTimestamp(timestamp) {
        if (!timestamp) return '-';
        const date = new Date(timestamp);
        return date.toLocaleString('ru-RU', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    },

    /**
     * Format JSON for display
     */
    formatJSON(jsonString) {
        if (!jsonString) return '';
        try {
            const obj = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
            return JSON.stringify(obj, null, 2);
        } catch (e) {
            return jsonString;
        }
    },

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => TransactionLogs.init());
} else {
    TransactionLogs.init();
}
