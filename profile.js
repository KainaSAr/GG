document.addEventListener('DOMContentLoaded', () => {
    const currentUser = getCurrentUser();
    if (!currentUser || !currentUser.username) {
        window.location.href = 'index.html';
        return;
    }

    document.querySelector('.profile-username').textContent = currentUser.username;
    
    renderWishlist();
    renderSearchHistory();

    function renderWishlist() {
        const container = document.querySelector('.wishlist-container');
        container.innerHTML = '';
        
        if (!currentUser.wishlist || currentUser.wishlist.length === 0) {
            container.innerHTML = '<p class="empty-message">Список желаемого пуст</p>';
            return;
        }
        
        currentUser.wishlist.forEach(item => {
            const gameElement = document.createElement('div');
            gameElement.className = 'wishlist-item';
            gameElement.innerHTML = `
                <img src="${item.thumb || 'https://via.placeholder.com/100'}" alt="${item.title}" class="wishlist-thumb">
                <div class="wishlist-info">
                    <h4>${item.title}</h4>
                    <span class="price">$${parseFloat(item.price).toFixed(2)}</span>
                </div>
                <button class="remove-btn" data-game-id="${item.gameID}">
                    <i class="fas fa-times"></i>
                </button>
            `;
            
            // Обработчик удаления из избранного
            const removeBtn = gameElement.querySelector('.remove-btn');
            removeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                removeFromWishlist(item.gameID);
                // Удаляем элемент из DOM без перезагрузки страницы
                gameElement.remove();
                
                // Если список пуст, показываем сообщение
                if (container.children.length === 0) {
                    container.innerHTML = '<p class="empty-message">Список желаемого пуст</p>';
                }
            });
            
            container.appendChild(gameElement);
        });
    }

    function renderSearchHistory() {
        const container = document.querySelector('.search-history-container');
        container.innerHTML = '';
        
        if (!currentUser.searchHistory || currentUser.searchHistory.length === 0) {
            container.innerHTML = '<p class="empty-message">История поиска пуста</p>';
            return;
        }
        
        currentUser.searchHistory.forEach(item => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            historyItem.innerHTML = `
                <span>${item.query}</span>
                <span class="search-count">${item.count} поиск(ов)</span>
                <button class="search-again" data-query="${item.query.replace(/"/g, '&quot;')}">
                    <i class="fas fa-search"></i>
                </button>
            `;
            
            // Обработчик поиска из истории
            const searchBtn = historyItem.querySelector('.search-again');
            searchBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const query = searchBtn.dataset.query;
                searchFromHistory(query);
            });
            
            container.appendChild(historyItem);
        });
    }
});

function searchFromHistory(query) {
    localStorage.setItem('lastSearch', query);
    window.location.href = 'search.html';
}