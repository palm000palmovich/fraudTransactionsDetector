// Login functionality
document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.getElementById('loginForm');

    loginForm.addEventListener('submit', function(e) {
        e.preventDefault();

        const username = document.getElementById('username').value;
        const password = document.getElementById('password').value;

        if (username && password) {
            // Store user info in sessionStorage
            sessionStorage.setItem('user', JSON.stringify({
                email: username,
                role: 'admin'
            }));

            // Redirect to dashboard
            window.location.href = 'dashboard.html';
        } else {
            alert('Пожалуйста, введите email и пароль');
        }
    });
});
