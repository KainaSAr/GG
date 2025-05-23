let users = JSON.parse(localStorage.getItem('gameFinderUsers')) || [];

function toggleAuthForms() {
    const registrationForm = document.querySelector('.registrationForm');
    const loginForm = document.querySelector('.loginForm');
    
    if (!registrationForm || !loginForm) return;
    
    registrationForm.style.display = registrationForm.style.display === 'none' ? 'block' : 'none';
    loginForm.style.display = loginForm.style.display === 'none' ? 'block' : 'none';
}

function register() {
    const username = document.querySelector('.regUsername')?.value.trim();
    const password = document.querySelector('.regPassword')?.value.trim();
    const email = document.querySelector('.regEmail')?.value.trim();

    if (!username || !password) {
        showAlert('Имя пользователя и пароль обязательны');
        return;
    }

    if (username.length < 3 || password.length < 6) {
        showAlert('Имя пользователя должно содержать минимум 3 символа, а пароль - 6 символов');
        return;
    }

    if (users.some(user => user.username.toLowerCase() === username.toLowerCase())) {
        showAlert('Пользователь с таким именем уже существует');
        return;
    }

    const newUser = {
        username,
        password: simpleHash(password),
        email: email || null,
        registrationDate: new Date().toISOString(),
        wishlist: [],
        searchHistory: []
    };

    users.push(newUser);
    localStorage.setItem('gameFinderUsers', JSON.stringify(users));
    localStorage.setItem('currentUser', JSON.stringify(newUser));
    
    showAlert(`Добро пожаловать, ${username}!`, 'success');
    setTimeout(() => window.location.href = 'search.html', 1500);
}

function login() {
    const username = document.querySelector('.loginUsername')?.value.trim();
    const password = document.querySelector('.loginPassword')?.value.trim();

    const user = users.find(user => 
        user.username.toLowerCase() === username.toLowerCase() && 
        user.password === simpleHash(password)
    );
    
    if (user) {
        localStorage.setItem('currentUser', JSON.stringify(user));
        showAlert(`С возвращением, ${username}!`, 'success');
        setTimeout(() => window.location.href = 'search.html', 1500);
    } else {
        showAlert('Неверное имя пользователя или пароль');
    }
}

function logout() {
    localStorage.removeItem('currentUser');
    window.location.href = 'index.html';
}

function getCurrentUser() {
    try {
        const userData = localStorage.getItem('currentUser');
        return userData ? JSON.parse(userData) : null;
    } catch (e) {
        console.error("Ошибка чтения данных пользователя:", e);
        return null;
    }
}

function simpleHash(str) {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i);
        hash = (hash << 5) - hash + char;
        hash = hash & hash;
    }
    return hash.toString();
}

function showAlert(message, type = 'error') {
    const alertBox = document.createElement('div');
    alertBox.className = `alert ${type}`;
    alertBox.textContent = message;
    document.body.appendChild(alertBox);
    
    setTimeout(() => {
        alertBox.classList.add('fade-out');
        setTimeout(() => alertBox.remove(), 300);
    }, 3000);
}

function initAuthModal() {
    const modal = document.querySelector('.auth-modal');
    const authButton = document.querySelector('.auth-button');
    const closeModal = document.querySelector('.auth-modal .close-modal');
    const forms = modal.querySelectorAll('input, button');
  forms.forEach(el => {
    el.style.fontSize = '16px'; // Убираем автоуменьшение в iOS
  });
    if (authButton) {
        authButton.addEventListener('click', (e) => {
            e.preventDefault();
            if (modal) modal.style.display = 'flex';
        });
    }
    
    if (closeModal) {
        closeModal.addEventListener('click', () => {
            if (modal) modal.style.display = 'none';
        });
    }
    
    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    initAuthModal();
});
