document.addEventListener('DOMContentLoaded', () => {
    checkAuthStatus();
    setupAuthModal();
    setupMobileMenu();
});

let users = JSON.parse(localStorage.getItem('gameFinderUsers')) || [];

function checkAuthStatus() {
    const currentUser = getCurrentUser();
    const authButton = document.querySelector('.auth-button');
    const userProfile = document.querySelector('.user-profile');
    
    if (currentUser) {
        if (authButton) authButton.style.display = 'none';
        if (userProfile) {
            document.querySelector('.username-display').textContent = currentUser.username;
            userProfile.style.display = 'flex';
            userProfile.onclick = (e) => {
                e.preventDefault();
                window.location.href = 'profile.html';
            };
        }
    } else {
        if (authButton) authButton.style.display = 'flex';
        if (userProfile) userProfile.style.display = 'none';
    }
}

function setupAuthModal() {
    const modal = document.querySelector('.auth-modal');
    const authButton = document.querySelector('.auth-button');
    const closeModal = document.querySelector('.auth-modal .close-modal');
    
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

function setupMobileMenu() {
    const mobileMenuButton = document.querySelector('.mobile-menu-button');
    const mainNav = document.querySelector('.main-nav');
    
    if (mobileMenuButton && mainNav) {
        mobileMenuButton.addEventListener('click', () => {
            mainNav.classList.toggle('active');
        });
    }
}

function toggleForms() {
    const registrationForm = document.querySelector('.registrationForm'); 
    const loginForm = document.querySelector('.loginForm'); 
    
    if (!registrationForm || !loginForm) return;
    
    if (registrationForm.style.display === 'none' || registrationForm.style.display === '') {
        registrationForm.style.display = 'block'; 
        loginForm.style.display = 'none'; 
    } else {
        registrationForm.style.display = 'none';
        loginForm.style.display = 'block'; 
    }
}

function register() {
    const username = document.querySelector('.regUsername').value.trim();
    const password = document.querySelector('.regPassword').value.trim();
    const email = document.querySelector('.regEmail')?.value.trim();

    if (username.length < 3 || password.length < 6) {
        alert("Имя пользователя должно содержать минимум 3 символа, а пароль - 6 символов."); 
        return; 
    }

    const userExists = users.some(user => user.username.toLowerCase() === username.toLowerCase());
    if (userExists) {
        alert("Пользователь с таким именем уже существует."); 
        return; 
    }

    const newUser = {
        username,
        password,
        email: email || null,
        registrationDate: new Date().toISOString(),
        wishlist: [],
        priceAlerts: [],
        searchHistory: []
    };

    users.push(newUser);
    localStorage.setItem('gameFinderUsers', JSON.stringify(users));
    localStorage.setItem('currentUser', JSON.stringify(newUser));
    
    alert(`Пользователь ${username} успешно зарегистрирован!`);
    window.location.href = 'search.html';
}

function login() {
    const username = document.querySelector('.loginUsername').value.trim(); 
    const password = document.querySelector('.loginPassword').value.trim(); 

    const user = users.find(user => 
        user.username.toLowerCase() === username.toLowerCase() && 
        user.password === password
    );
    
    if (user) {
        localStorage.setItem('currentUser', JSON.stringify(user));
        window.location.href = 'search.html';
    } else {
        alert("Неверное имя пользователя или пароль."); 
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
        console.error("Ошибка при чтении данных пользователя:", e);
        return null;
    }
}

function saveUserData(user) {
    const users = JSON.parse(localStorage.getItem('gameFinderUsers')) || [];
    const index = users.findIndex(u => u.username === user.username);
    
    if (index !== -1) {
        users[index] = user;
        localStorage.setItem('gameFinderUsers', JSON.stringify(users));
        localStorage.setItem('currentUser', JSON.stringify(user));
    }
}
// Добавить эти функции в существующий script.js

function addToWishlist(game) {
    const currentUser = getCurrentUser();
    if (!currentUser) return false;

    if (!currentUser.wishlist) currentUser.wishlist = [];

    // Проверяем, есть ли уже игра в избранном
    const existingIndex = currentUser.wishlist.findIndex(item => item.gameID === game.gameID);
    
    if (existingIndex === -1) {
        currentUser.wishlist.push({
            gameID: game.gameID,
            title: game.external,
            price: game.cheapest,
            addedDate: new Date().toISOString(),
            thumb: game.thumb
        });
        saveUserData(currentUser);
        return true;
    }
    return false;
}

function addToSearchHistory(query) {
    const currentUser = getCurrentUser();
    if (!currentUser) return;

    if (!currentUser.searchHistory) currentUser.searchHistory = [];

    const existingItem = currentUser.searchHistory.find(item => item.query === query);
    if (existingItem) {
        existingItem.date = new Date().toISOString();
        existingItem.count++;
    } else {
        currentUser.searchHistory.unshift({
            query,
            date: new Date().toISOString(),
            count: 1
        });
        
        if (currentUser.searchHistory.length > 10) {
            currentUser.searchHistory.pop();
        }
    }
    saveUserData(currentUser);
}
// Обновленные функции для работы с избранным
function addToWishlist(game) {
    const currentUser = getCurrentUser();
    if (!currentUser) return false;

    if (!currentUser.wishlist) currentUser.wishlist = [];

    if (!currentUser.wishlist.some(item => item.gameID === game.gameID)) {
        currentUser.wishlist.push({
            gameID: game.gameID,
            title: game.external,
            price: game.cheapest,
            addedDate: new Date().toISOString(),
            thumb: game.thumb
        });
        saveUserData(currentUser);
        return true;
    }
    return false;
}

function removeFromWishlist(gameID) {
    let currentUser = getCurrentUser();
    if (!currentUser) return false;
    
    const initialLength = currentUser.wishlist ? currentUser.wishlist.length : 0;
    currentUser.wishlist = currentUser.wishlist.filter(item => item.gameID !== gameID);
    
    if (currentUser.wishlist.length !== initialLength) {
        saveUserData(currentUser);
        return true;
    }
    return false;
}


function checkAuthStatus() {
    const currentUser = getCurrentUser();
    const authButton = document.querySelector('.auth-button');
    const userProfile = document.querySelector('.user-profile');
    const usernameDisplay = document.querySelector('.username-display');
    
    if (currentUser) {
        if (authButton) authButton.style.display = 'none';
        if (userProfile) {
            if (usernameDisplay) usernameDisplay.textContent = currentUser.username;
            userProfile.style.display = 'flex';
            userProfile.onclick = (e) => {
                e.preventDefault();
                window.location.href = 'profile.html';
            };
        }
    } else {
        if (authButton) authButton.style.display = 'flex';
        if (userProfile) userProfile.style.display = 'none';
        if (usernameDisplay) usernameDisplay.textContent = '';
    }
}