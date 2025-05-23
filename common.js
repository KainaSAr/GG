function checkAuthStatus() {
    const currentUser = getCurrentUser();
    const authButton = document.querySelector('.auth-button');
    const userProfile = document.querySelector('.user-profile');
    const usernameDisplay = document.querySelector('.username-display');
    
    if (currentUser) {
        if (authButton) authButton.style.display = 'none';
        if (userProfile) {
            userProfile.style.display = 'flex';
            if (usernameDisplay) usernameDisplay.textContent = currentUser.username;
        }
    } else {
        if (authButton) authButton.style.display = 'flex';
        if (userProfile) userProfile.style.display = 'none';
        if (usernameDisplay) usernameDisplay.textContent = '';
    }
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
        return saveUserData(currentUser);
    }
    return false;
}

document.addEventListener('DOMContentLoaded', () => {
    checkAuthStatus();
    setupMobileMenu();
});
function setupMobileMenu() {
  const mobileMenuButton = document.querySelector('.mobile-menu-button');
  const mainNav = document.querySelector('.main-nav');
  
  if (mobileMenuButton && mainNav) {
    mobileMenuButton.addEventListener('click', () => {
      mainNav.classList.toggle('active');
      mobileMenuButton.innerHTML = mainNav.classList.contains('active') 
        ? '<i class="fas fa-times"></i>' 
        : '<i class="fas fa-bars"></i>';
    });
  }
}
document.addEventListener('touchstart', function() {}, {passive: true});