// Authentication module for Financial Radar

const Auth = {
    // Save credentials to sessionStorage
    saveCredentials(username, password) {
        const credentials = btoa(username + ':' + password);
        sessionStorage.setItem('credentials', credentials);
        sessionStorage.setItem('username', username);

        // Also save user object for backward compatibility
        const role = this.getRole();
        const userObj = {
            username: username,
            email: username, // Use username as email for now
            role: role
        };
        sessionStorage.setItem('user', JSON.stringify(userObj));
    },

    // Get stored credentials
    getCredentials() {
        return sessionStorage.getItem('credentials');
    },

    // Get username
    getUsername() {
        return sessionStorage.getItem('username');
    },

    // Clear authentication data
    logout() {
        sessionStorage.removeItem('credentials');
        sessionStorage.removeItem('username');
        sessionStorage.removeItem('role');
        sessionStorage.removeItem('user'); // Remove user object too
        window.location.href = 'login.html';
    },

    // Check if user is authenticated
    isAuthenticated() {
        return this.getCredentials() !== null;
    },

    // Get role from backend or stored value
    getRole() {
        return sessionStorage.getItem('role') || 'VIEWER';
    },

    // Save role
    saveRole(role) {
        sessionStorage.setItem('role', role);
    },

    // Test credentials by making API call
    async testCredentials(username, password) {
        const credentials = btoa(username + ':' + password);

        try {
            // Call /api/auth/me to verify credentials and get user info
            const response = await fetch('/api/auth/me', {
                method: 'GET',
                headers: {
                    'Authorization': 'Basic ' + credentials
                }
            });

            if (response.status === 200) {
                // Parse user info from backend
                const userInfo = await response.json();
                const role = userInfo.role;

                // Save role from backend
                this.saveRole(role);

                // Save credentials (this also saves user object)
                this.saveCredentials(username, password);

                return { success: true, role };
            } else if (response.status === 403) {
                // Authenticated but no access
                return { success: false, error: 'Недостаточно прав для доступа к системе' };
            } else if (response.status === 401) {
                return { success: false, error: 'Неверные учетные данные' };
            } else {
                return { success: false, error: `HTTP ${response.status}: ${response.statusText}` };
            }
        } catch (error) {
            console.error('Auth error:', error);
            return { success: false, error: 'Ошибка подключения к серверу: ' + error.message };
        }
    },

    // Make authenticated API request
    async fetch(url, options = {}) {
        const credentials = this.getCredentials();

        if (!credentials) {
            window.location.href = 'login.html';
            throw new Error('Not authenticated');
        }

        const headers = {
            ...options.headers,
            'Authorization': 'Basic ' + credentials
        };

        if (options.body && typeof options.body === 'object') {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(options.body);
        }

        const response = await fetch(url, {
            ...options,
            headers
        });

        // If unauthorized, redirect to login
        if (response.status === 401) {
            this.logout();
            throw new Error('Session expired');
        }

        return response;
    },

    // Check if user has ADMIN role
    isAdmin() {
        return this.getRole() === 'ADMIN';
    },

    // Check if user has VIEWER role
    isViewer() {
        return this.getRole() === 'VIEWER';
    },

    // Initialize auth check for protected pages
    requireAuth() {
        if (!this.isAuthenticated()) {
            window.location.href = 'login.html';
        }
    }
};

// Make Auth available globally
window.Auth = Auth;
