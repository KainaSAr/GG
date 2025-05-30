document.addEventListener('DOMContentLoaded', () => {
    const btn_request = document.querySelector(".btn_request");
    const main_inp = document.querySelector(".main-inp");
    const resultsDiv = document.querySelector(".results");
    const loadingDiv = document.querySelector(".loading");
    const sortSelect = document.getElementById("sortBy");
    const priceSlider = document.getElementById("maxPrice");
    const priceValue = document.getElementById("priceValue");

    let currentGames = [];


    const lastSearch = localStorage.getItem('lastSearch');
    if (lastSearch && main_inp) {
        main_inp.value = lastSearch;
        searchGames();
    }

    if (priceSlider && priceValue) {
        priceSlider.addEventListener('input', () => {
            priceValue.textContent = priceSlider.value;
            if (currentGames.length > 0) {
                filterAndDisplayGames();
            }
        });
    }

    if (sortSelect) {
        sortSelect.addEventListener('change', () => {
            if (currentGames.length > 0) {
                filterAndDisplayGames();
            }
        });
    }

    if (btn_request) {
        btn_request.addEventListener('click', searchGames);
    }

    if (main_inp) {
        main_inp.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                searchGames();
            }
        });
    }

    function searchGames() {
        if (!main_inp) return;
        
        const gameTitle = main_inp.value.trim();
        
        if (!gameTitle) {
            if (resultsDiv) {
                resultsDiv.innerHTML = '<div class="no-results">Пожалуйста, введите название игры.</div>';
            }
            return;
        }
        
        const currentUser = getCurrentUser();
        if (currentUser) {
            addToSearchHistory(gameTitle);
        }
        
        if (loadingDiv) loadingDiv.style.display = 'flex';
        if (resultsDiv) resultsDiv.innerHTML = '';
        
        fetch(`https://www.cheapshark.com/api/1.0/games?title=${encodeURIComponent(gameTitle)}`)
            .then(response => {
                if (!response.ok) throw new Error('Ошибка сети');
                return response.json();
            })
            .then(games => {
                currentGames = games || [];
                filterAndDisplayGames();
            })
            .catch(error => {
                console.error(error);
                if (resultsDiv) {
                    resultsDiv.innerHTML = `
                        <div class="error">
                            <i class="fas fa-exclamation-triangle"></i>
                            <p>Произошла ошибка при получении данных</p>
                            <small>${error.message || ''}</small>
                        </div>
                    `;
                }
            })
            .finally(() => {
                if (loadingDiv) loadingDiv.style.display = 'none';
            });
    }

    function filterAndDisplayGames() {
        if (!resultsDiv) return;
        
        if (currentGames.length === 0) {
            resultsDiv.innerHTML = '<div class="no-results">Игры не найдены.</div>';
            return;
        }
        
        const maxPrice = parseFloat(priceSlider?.value || 50);
        let filteredGames = currentGames.filter(game => {
            const price = parseFloat(game.cheapest) || 0;
            return price <= maxPrice;
        });
        
        const sortValue = sortSelect?.value || '';
        filteredGames.sort((a, b) => {
            switch (sortValue) {
                case 'price-asc': return (parseFloat(a.cheapest) || 0) - (parseFloat(b.cheapest) || 0);
                case 'price-desc': return (parseFloat(b.cheapest) || 0) - (parseFloat(a.cheapest) || 0);
                case 'name-asc': return (a.external || '').localeCompare(b.external || '');
                case 'name-desc': return (b.external || '').localeCompare(a.external || '');
                default: return 0;
            }
        });
        
        resultsDiv.innerHTML = '';
        
        if (filteredGames.length === 0) {
            resultsDiv.innerHTML = '<div class="no-results">Нет игр по выбранным фильтрам.</div>';
            return;
        }
        
        filteredGames.forEach(game => {
            const gameCard = createGameCard(game);
            resultsDiv.appendChild(gameCard);
        });
    }

    function createGameCard(game) {
        const gameCard = document.createElement('div');
        gameCard.className = 'game-card';
        
        const price = parseFloat(game.cheapest);
        const priceDisplay = isNaN(price) ? 'Цена не указана' : `$${price.toFixed(2)}`;
        const currentUser = getCurrentUser();
        let isInWishlist = currentUser?.wishlist?.some(item => item.gameID === game.gameID) || false;
        
        gameCard.innerHTML = `
            ${game.thumb ? `<img src="${game.thumb}" alt="${game.external}" class="game-image" loading="lazy">` : '<div class="game-image">No image</div>'}
            <div class="game-title">${game.external || 'Название не указано'}</div>
            <div class="game-price">${priceDisplay}</div>
            <button class="wishlist-button ${isInWishlist ? 'in-wishlist' : ''}" 
                    data-game-id="${game.gameID}">
                <i class="${isInWishlist ? 'fas' : 'far'} fa-heart"></i> 
                ${isInWishlist ? 'В избранном' : 'В избранное'}
            </button>
        `;
        
        gameCard.addEventListener('click', (e) => {
            if (e.target.closest('.wishlist-button')) return;
            if (game.gameID) {
                window.open(`https://www.cheapshark.com/redirect?dealID=${game.cheapestDealID}`, '_blank');
            }
        });
        
        const wishlistBtn = gameCard.querySelector('.wishlist-button');
        if (wishlistBtn) {
            wishlistBtn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const currentUser = getCurrentUser();
                
                if (!currentUser) {
                    showAlert('Для добавления в избранное необходимо авторизоваться');
                    return;
                }
                
                const gameId = wishlistBtn.dataset.gameId;
                const gameData = currentGames.find(g => g.gameID === gameId);
                
                if (isInWishlist) {
                    const success = await removeFromWishlist(gameId);
                    if (success) {
                        isInWishlist = false;
                        wishlistBtn.innerHTML = `<i class="far fa-heart"></i> В избранное`;
                        wishlistBtn.classList.remove('in-wishlist');
                    }
                } else {
                    const success = await addToWishlist(gameData);
                    if (success) {
                        isInWishlist = true;
                        wishlistBtn.innerHTML = `<i class="fas fa-heart"></i> В избранном`;
                        wishlistBtn.classList.add('in-wishlist');
                    }
                }
            });
        }
        
        return gameCard;
    }
});