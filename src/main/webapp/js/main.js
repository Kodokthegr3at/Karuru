// Main utility JavaScript file

// Initialize animations
if (typeof document !== 'undefined') {
    if (!document.getElementById('karuru-animations-style')) {
        const style = document.createElement('style');
        style.id = 'karuru-animations-style';
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; transform: translateY(20px); }
                to { opacity: 1; transform: translateY(0); }
            }
            .fade-in { animation: fadeIn 0.5s ease-in; }
        `;
        document.head.appendChild(style);
    }
}

// KaruruUtils namespace
window.KaruruUtils = window.KaruruUtils || {};

// Format price
KaruruUtils.formatPrice = function(amount) {
    if (amount == null || amount === undefined) return '¥0';
    if (typeof amount === 'string') {
        amount = parseFloat(amount);
    }
    if (isNaN(amount)) return '¥0';
    return '¥' + amount.toLocaleString('ja-JP');
};

// Format date
KaruruUtils.formatDate = function(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return dateString;
    
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);
    
    if (diffMins < 1) return 'たった今';
    if (diffMins < 60) return `${diffMins}分前`;
    if (diffHours < 24) return `${diffHours}時間前`;
    if (diffDays < 7) return `${diffDays}日前`;
    
    return date.toLocaleDateString('ja-JP', { 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric' 
    });
};

// Show loading
KaruruUtils.showLoading = function(container) {
    if (!container) return;
    container.innerHTML = `
        <div class="col-12 text-center py-5">
            <div class="spinner-border text-primary" role="status">
                <span class="visually-hidden">読み込み中...</span>
            </div>
        </div>
    `;
};

// Show notification - compact, clear, easy to understand (mobile & PC)
KaruruUtils.showNotification = function(message, type = 'info') {
    const config = {
        'success': { class: 'alert-success', icon: 'bi-check-circle-fill' },
        'error': { class: 'alert-danger', icon: 'bi-exclamation-triangle-fill' },
        'danger': { class: 'alert-danger', icon: 'bi-exclamation-triangle-fill' },
        'warning': { class: 'alert-warning', icon: 'bi-exclamation-circle-fill' },
        'info': { class: 'alert-info', icon: 'bi-info-circle-fill' }
    };
    const cfg = config[type] || config.info;

    // Dismiss previous "info" (processing) when showing error/danger/success
    const container = document.getElementById('karuru-notification-container');
    if (container && (type === 'danger' || type === 'error' || type === 'success')) {
        container.querySelectorAll('.karuru-toast.alert-info').forEach(n => {
            n.classList.remove('show');
            setTimeout(() => n.remove(), 200);
        });
    }

    if (!container) {
        const newContainer = document.createElement('div');
        newContainer.id = 'karuru-notification-container';
        newContainer.className = 'karuru-notification-container';
        document.body.appendChild(newContainer);
    }
    const cont = document.getElementById('karuru-notification-container');

    // Limit to 3 visible notifications
    const toasts = cont.querySelectorAll('.karuru-toast');
    if (toasts.length >= 3) {
        toasts[0].classList.remove('show');
        setTimeout(() => toasts[0].remove(), 200);
    }

    const notification = document.createElement('div');
    notification.className = `karuru-toast alert ${cfg.class} alert-dismissible fade show`;
    notification.setAttribute('role', 'alert');
    const safeMessage = (message != null) ? String(message) : '';
    notification.innerHTML = `
        <i class="bi ${cfg.icon} karuru-toast-icon flex-shrink-0"></i>
        <span class="karuru-toast-message">${escapeHtml(safeMessage)}</span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="閉じる"></button>
    `;

    cont.appendChild(notification);

    const closeBtn = notification.querySelector('.btn-close');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            notification.classList.remove('show');
            setTimeout(() => notification.remove(), 300);
        });
    }

    const duration = (type === 'danger' || type === 'error') ? 8000 : 5000;
    setTimeout(() => {
        if (notification.parentNode) {
            notification.classList.remove('show');
            setTimeout(() => notification.remove(), 300);
        }
    }, duration);
};

// Dismiss all toast notifications (e.g. when opening Other menu to prevent overlap)
KaruruUtils.dismissAllNotifications = function() {
    const container = document.getElementById('karuru-notification-container');
    if (container) {
        container.querySelectorAll('.karuru-toast').forEach(n => {
            n.classList.remove('show');
            setTimeout(() => n.remove(), 200);
        });
    }
};

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Confirm dialog
KaruruUtils.confirmDialog = function(message) {
    return confirm(message);
};

// ============================================
// BADGE COUNTER FUNCTIONS
// ============================================

// Update cart badge
window.updateCartCount = async function() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/CartServlet?action=getCartCount`);
        if (response.ok) {
            const data = await response.json();
            const count = data.count || data.cartCount || 0;
            
            // Update desktop cart badge
            const badge = document.getElementById('cartBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count > 99 ? '99+' : count.toString();
                    badge.style.display = 'inline-flex';
                    badge.style.visibility = 'visible';
                    badge.style.opacity = '1';
                    badge.setAttribute('data-count', count);
                    badge.removeAttribute('data-count-zero');
                } else {
                    badge.textContent = ''; // Clear text content
                    badge.style.display = 'none';
                    badge.style.visibility = 'hidden';
                    badge.style.opacity = '0';
                    badge.setAttribute('data-count', '0');
                    badge.setAttribute('data-count-zero', 'true');
                }
            }
            
            // Update mobile cart badge
            const mobileBadge = document.getElementById('mobileCartBadge');
            if (mobileBadge) {
                if (count > 0) {
                    mobileBadge.textContent = count > 99 ? '99+' : count.toString();
                    mobileBadge.style.display = 'flex';
                    mobileBadge.style.visibility = 'visible';
                    mobileBadge.style.opacity = '1';
                    mobileBadge.setAttribute('data-count', count);
                    mobileBadge.removeAttribute('data-count-zero');
                } else {
                    mobileBadge.textContent = ''; // Clear text content
                    mobileBadge.style.display = 'none';
                    mobileBadge.style.visibility = 'hidden';
                    mobileBadge.style.opacity = '0';
                    mobileBadge.setAttribute('data-count', '0');
                    mobileBadge.setAttribute('data-count-zero', 'true');
                }
            }
        }
    } catch (error) {
        console.error('Error updating cart count:', error);
    }
};

// Update message badge
window.updateMessageBadge = async function() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/MessagesServlet?action=getUnreadCount`);
        if (response.ok) {
            const data = await response.json();
            const count = data.count || data.unread_count || 0;
            // Update main badge (if exists)
            const badge = document.getElementById('messageBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count > 99 ? '99+' : count.toString();
                    badge.style.display = 'inline-flex';
                    badge.style.visibility = 'visible';
                    badge.style.opacity = '1';
                    badge.setAttribute('data-count', count);
                    badge.removeAttribute('data-count-zero');
                } else {
                    badge.style.display = 'none';
                    badge.style.visibility = 'hidden';
                    badge.style.opacity = '0';
                    badge.setAttribute('data-count', '0');
                    badge.setAttribute('data-count-zero', 'true');
                }
            }
            // Update dropdown badge
            const dropdownBadge = document.getElementById('messageBadgeDropdown');
            if (dropdownBadge) {
                if (count > 0) {
                    dropdownBadge.textContent = count > 99 ? '99+' : count.toString();
                    dropdownBadge.style.display = 'inline-flex';
                    dropdownBadge.style.visibility = 'visible';
                    dropdownBadge.style.opacity = '1';
                    dropdownBadge.setAttribute('data-count', count);
                    dropdownBadge.removeAttribute('data-count-zero');
                } else {
                    dropdownBadge.textContent = ''; // Clear text content
                    dropdownBadge.style.display = 'none';
                    dropdownBadge.style.visibility = 'hidden';
                    dropdownBadge.style.opacity = '0';
                    dropdownBadge.setAttribute('data-count', '0');
                    dropdownBadge.setAttribute('data-count-zero', 'true');
                }
            }
            // Update mobile badge (その他 modal)
            const mobileBadge = document.getElementById('mobileMessageBadge');
            if (mobileBadge) {
                if (count > 0) {
                    mobileBadge.textContent = count > 99 ? '99+' : count.toString();
                    mobileBadge.style.display = 'inline-flex';
                    mobileBadge.style.visibility = 'visible';
                    mobileBadge.style.opacity = '1';
                    mobileBadge.setAttribute('data-count', count);
                    mobileBadge.removeAttribute('data-count-zero');
                } else {
                    mobileBadge.textContent = '';
                    mobileBadge.style.display = 'none';
                    mobileBadge.style.visibility = 'hidden';
                    mobileBadge.style.opacity = '0';
                    mobileBadge.setAttribute('data-count', '0');
                    mobileBadge.setAttribute('data-count-zero', 'true');
                }
            }
            // Update combined badge
            updateCombinedBadge();
        }
    } catch (error) {
        console.error('Error updating message badge:', error);
    }
};

// Update notification badge
window.updateNotificationBadge = function(count) {
    // Update main badge (if exists)
    const badge = document.getElementById('notificationBadge');
    if (badge) {
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count.toString();
            badge.style.display = 'inline-flex';
            badge.style.visibility = 'visible';
            badge.style.opacity = '1';
            badge.setAttribute('data-count', count);
            badge.removeAttribute('data-count-zero');
        } else {
            badge.style.display = 'none';
            badge.style.visibility = 'hidden';
            badge.style.opacity = '0';
            badge.setAttribute('data-count', '0');
            badge.setAttribute('data-count-zero', 'true');
        }
    }
    // Update dropdown badge
    const dropdownBadge = document.getElementById('notificationBadgeDropdown');
    if (dropdownBadge) {
        if (count > 0) {
            dropdownBadge.textContent = count > 99 ? '99+' : count.toString();
            dropdownBadge.style.display = 'inline-flex';
            dropdownBadge.style.visibility = 'visible';
            dropdownBadge.style.opacity = '1';
            dropdownBadge.setAttribute('data-count', count);
            dropdownBadge.removeAttribute('data-count-zero');
        } else {
            dropdownBadge.textContent = ''; // Clear text content
            dropdownBadge.style.display = 'none';
            dropdownBadge.style.visibility = 'hidden';
            dropdownBadge.style.opacity = '0';
            dropdownBadge.setAttribute('data-count', '0');
            dropdownBadge.setAttribute('data-count-zero', 'true');
        }
    }
    // Update mobile badge (その他 modal)
    const mobileBadge = document.getElementById('mobileNotificationBadge');
    if (mobileBadge) {
        if (count > 0) {
            mobileBadge.textContent = count > 99 ? '99+' : count.toString();
            mobileBadge.style.display = 'inline-flex';
            mobileBadge.style.visibility = 'visible';
            mobileBadge.style.opacity = '1';
            mobileBadge.setAttribute('data-count', count);
            mobileBadge.removeAttribute('data-count-zero');
        } else {
            mobileBadge.textContent = '';
            mobileBadge.style.display = 'none';
            mobileBadge.style.visibility = 'hidden';
            mobileBadge.style.opacity = '0';
            mobileBadge.setAttribute('data-count', '0');
            mobileBadge.setAttribute('data-count-zero', 'true');
        }
    }
    // Update combined badge
    updateCombinedBadge();
};

// Update combined badge for "その他" dropdown
function updateCombinedBadge() {
    const messageBadge = document.getElementById('messageBadgeDropdown');
    const notificationBadge = document.getElementById('notificationBadgeDropdown');
    const combinedBadge = document.getElementById('combinedBadge');
    
    if (!combinedBadge) return;
    
    let messageCount = 0;
    let notificationCount = 0;
    
    // Check message badge - only count if visible and not 0
    if (messageBadge) {
        const isVisible = messageBadge.style.display !== 'none' && 
                         messageBadge.style.visibility !== 'hidden' &&
                         messageBadge.getAttribute('data-count') !== '0' &&
                         messageBadge.getAttribute('data-count-zero') !== 'true';
        if (isVisible) {
            const count = parseInt(messageBadge.textContent) || 0;
            if (count > 0) {
                messageCount = count;
            }
        }
    }
    
    // Check notification badge - only count if visible and not 0
    if (notificationBadge) {
        const isVisible = notificationBadge.style.display !== 'none' && 
                         notificationBadge.style.visibility !== 'hidden' &&
                         notificationBadge.getAttribute('data-count') !== '0' &&
                         notificationBadge.getAttribute('data-count-zero') !== 'true';
        if (isVisible) {
            const count = parseInt(notificationBadge.textContent) || 0;
            if (count > 0) {
                notificationCount = count;
            }
        }
    }
    
    const totalCount = messageCount + notificationCount;
    
    if (totalCount > 0) {
        combinedBadge.textContent = totalCount > 99 ? '99+' : totalCount.toString();
        combinedBadge.style.display = 'inline-flex';
        combinedBadge.style.visibility = 'visible';
        combinedBadge.style.opacity = '1';
        combinedBadge.removeAttribute('data-count-zero');
        combinedBadge.setAttribute('data-count', totalCount);
    } else {
        combinedBadge.textContent = ''; // Clear text content
        combinedBadge.style.display = 'none';
        combinedBadge.style.visibility = 'hidden';
        combinedBadge.style.opacity = '0';
        combinedBadge.setAttribute('data-count', '0');
        combinedBadge.setAttribute('data-count-zero', 'true');
    }
    
    // Update mobile combined badge (その他 button in bottom nav)
    const mobileCombinedBadge = document.getElementById('mobileCombinedBadge');
    if (mobileCombinedBadge) {
        if (totalCount > 0) {
            mobileCombinedBadge.textContent = totalCount > 99 ? '99+' : totalCount.toString();
            mobileCombinedBadge.style.display = 'inline-flex';
            mobileCombinedBadge.style.visibility = 'visible';
            mobileCombinedBadge.style.opacity = '1';
            mobileCombinedBadge.setAttribute('data-count', totalCount);
            mobileCombinedBadge.removeAttribute('data-count-zero');
        } else {
            mobileCombinedBadge.textContent = ''; // Clear text content
            mobileCombinedBadge.style.display = 'none';
            mobileCombinedBadge.style.visibility = 'hidden';
            mobileCombinedBadge.style.opacity = '0';
            mobileCombinedBadge.setAttribute('data-count', '0');
            mobileCombinedBadge.setAttribute('data-count-zero', 'true');
        }
    }
}

// Update favorite icon in header
window.updateFavoriteIcon = async function() {
    try {
        // Find icon by searching for heart icon in favorites nav link
        // Try multiple selectors to find the icon
        let icon = document.getElementById('favoritesNavIcon');
        if (!icon) {
            // Try finding by parent link
            const favoritesLink = document.querySelector('a[href*="favorites.jsp"]');
            if (favoritesLink) {
                icon = favoritesLink.querySelector('i.bi-heart, i.bi-heart-fill');
            }
        }
        
        if (!icon) {
            // Try finding by text content
            const navLinks = document.querySelectorAll('.nav-link');
            for (let link of navLinks) {
                if (link.textContent && link.textContent.includes('お気に入り')) {
                    icon = link.querySelector('i.bi-heart, i.bi-heart-fill');
                    if (icon) break;
                }
            }
        }
        
        if (!icon) {
            console.warn('[FavoriteIcon] Icon not found in DOM');
            return;
        }
        
        // Check if KaruruUtils is available
        if (!window.KaruruUtils || !window.KaruruUtils.apiFetch) {
            console.warn('[FavoriteIcon] KaruruUtils not available yet');
            return;
        }
        
        // Check if user is logged in
        if (!window.currentUserId) {
            // Default to outline heart if not logged in
            icon.classList.remove('bi-heart-fill');
            icon.classList.add('bi-heart');
            icon.style.color = '';
            return;
        }
        
        const url = `${window.CONTEXT_PATH}/FavoriteServlet?action=getCount`;
        const data = await KaruruUtils.apiFetch(url);
        const count = parseInt(data?.count || data?.favoriteCount || data?.favorite_count || 0);
        
        if (count > 0) {
            // Change to filled heart if there are favorites
            icon.classList.remove('bi-heart');
            icon.classList.add('bi-heart-fill');
            icon.style.color = '#dc3545'; // Red color for filled heart
        } else {
            // Change to outline heart if no favorites
            icon.classList.remove('bi-heart-fill');
            icon.classList.add('bi-heart');
            icon.style.color = ''; // Reset to default color
        }
    } catch (error) {
        console.error('[FavoriteIcon] Error updating favorite icon:', error);
        // On error, try to set to outline heart
        const favoritesLink = document.querySelector('a[href*="favorites.jsp"]');
        if (favoritesLink) {
            const icon = favoritesLink.querySelector('i.bi-heart, i.bi-heart-fill');
            if (icon) {
                icon.classList.remove('bi-heart-fill');
                icon.classList.add('bi-heart');
                icon.style.color = '';
            }
        }
    }
};

// Update notification badge count
window.updateNotificationBadgeCount = async function() {
    if (!window.currentUserId) return;
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/NotificationsServlet?action=getUnreadCount`);
        if (response.ok) {
            const text = await response.text();
            let data = {};
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse notification badge response:', e);
                return;
            }
            
            if (data.success !== false) {
                const count = data.count || data.unread_count || data.unreadCount || 0;
                if (window.updateNotificationBadge) {
                    window.updateNotificationBadge(count);
                }
            }
        }
    } catch (error) {
        console.error('Error updating notification badge:', error);
    }
};

// Update all badges
window.updateAllBadges = async function() {
    await Promise.all([
        window.updateCartCount(),
        window.updateMessageBadge(),
        window.updateNotificationBadgeCount(),
        window.updateFavoriteIcon()
    ]);
};

// Initialize badges on page load
if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
        if (window.currentUserId) {
            window.updateAllBadges();
            // Update badges every 30 seconds
            setInterval(window.updateAllBadges, 30000);
        }
    });
    } else {
        // DOM is already ready
    if (window.currentUserId) {
        window.updateAllBadges();
        setInterval(window.updateAllBadges, 30000);
        }
    }
}

// ============================================
// API UTILITY FUNCTIONS
// ============================================

/**
 * Universal API fetch function with robust error handling
 * @param {string} url - The API endpoint URL
 * @param {object} options - Fetch options (method, headers, body, etc.)
 * @returns {Promise<object>} Parsed JSON response
 */
KaruruUtils.apiFetch = async function(url, options = {}) {
    const defaultOptions = {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
            ...options.headers
        },
        credentials: 'same-origin'
    };
    
    const mergedOptions = { ...defaultOptions, ...options };
    
    try {
        const response = await fetch(url, mergedOptions);
        
        // Handle non-OK responses
        if (!response.ok) {
            const errorText = await response.text().catch(() => 'Unknown error');
            
            // Check if response is HTML (likely a 404 error page)
            if (errorText.trim().startsWith('<!DOCTYPE') || errorText.trim().startsWith('<html')) {
                if (response.status === 404) {
                    throw new Error(`Servlet not found (404). Please ensure the servlet is compiled and deployed. URL: ${url}`);
                } else {
                    throw new Error(`Server returned HTML instead of JSON. Status: ${response.status}. URL: ${url}`);
                }
            }
            
            let errorData = null;
            try {
                errorData = JSON.parse(errorText);
            } catch (e) {
                // Not JSON, use text as error message
            }
            
            const errorMessage = errorData?.error || errorData?.message || errorText || `HTTP error! status: ${response.status}`;
            throw new Error(errorMessage);
        }
        
        // Parse response
        const contentType = response.headers.get('content-type') || '';
        let data;
        
        if (contentType.includes('application/json')) {
            const text = await response.text();
            if (!text || text.trim() === '') {
                return { success: true, data: null };
            }
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse JSON response:', e, 'Response text:', text);
                throw new Error('Invalid JSON response from server');
            }
        } else {
            const text = await response.text();
            if (!text || text.trim() === '') {
                return { success: true, data: null };
            }
            try {
                data = JSON.parse(text);
            } catch (e) {
                // If not JSON, return as text
                return { success: true, data: text };
            }
        }
        
        return data;
    } catch (error) {
        console.error('API fetch error:', error);
        throw error;
    }
};

/**
 * Extract data from API response (handles various response formats)
 * @param {object} response - API response object
 * @param {string} dataKey - Key to extract (e.g., 'products', 'data')
 * @returns {array|object} Extracted data
 */
KaruruUtils.extractData = function(response, dataKey = null) {
    if (!response) return null;
    
    // If response is already an array
    if (Array.isArray(response)) {
        return response;
    }
    
    // If response has success: false
    if (response.success === false) {
        return null;
    }
    
    // Try to extract data using common keys
    if (dataKey && response[dataKey]) {
        return response[dataKey];
    }
    
    // Try common data keys
    const commonKeys = ['data', 'items', 'products', 'categories', 'banners', 'orders', 'messages', 'notifications', 'sales'];
    for (const key of commonKeys) {
        if (response[key] !== undefined) {
            return response[key];
        }
    }
    
    // If response itself is the data (and not an error object)
    if (response.error || response.message) {
        return null;
    }
    
    return response;
};

/**
 * Safe JSON parse with fallback
 * @param {string} text - JSON string to parse
 * @param {*} fallback - Fallback value if parsing fails
 * @returns {*} Parsed object or fallback
 */
KaruruUtils.safeJsonParse = function(text, fallback = null) {
    if (!text || typeof text !== 'string') return fallback;
    try {
        return JSON.parse(text);
    } catch (e) {
        console.warn('JSON parse failed:', e);
        return fallback;
    }
};

/**
 * Build URL with query parameters
 * @param {string} baseUrl - Base URL
 * @param {object} params - Query parameters object
 * @returns {string} Complete URL with query string
 */
KaruruUtils.buildUrl = function(baseUrl, params = {}) {
    const url = new URL(baseUrl, window.location.origin);
    Object.keys(params).forEach(key => {
        if (params[key] !== null && params[key] !== undefined && params[key] !== '') {
            url.searchParams.append(key, params[key]);
        }
    });
    return url.pathname + url.search;
};

/**
 * Resolve image URL with proper context path handling
 * Handles absolute URLs, absolute paths, and relative paths
 * @param {string} imageUrl - Image URL from database or API
 * @param {string} defaultImage - Default image path (e.g., '/img/default-product.png')
 * @returns {string} Resolved image URL
 */
KaruruUtils.resolveImageUrl = function(imageUrl, defaultImage = '/img/default-product.png') {
    // If no image URL provided, return default
    if (!imageUrl || imageUrl.trim() === '' || imageUrl === 'null' || imageUrl === 'undefined') {
        const contextPath = window.CONTEXT_PATH || '';
        return defaultImage.startsWith('/') ? `${contextPath}${defaultImage}` : `${contextPath}/${defaultImage}`;
    }
    
    const contextPath = window.CONTEXT_PATH || '';
    const trimmedUrl = imageUrl.trim();
    
    // If it's already an absolute URL (http:// or https://), use as is
    if (trimmedUrl.startsWith('http://') || trimmedUrl.startsWith('https://')) {
        return trimmedUrl;
    }
    
    // If it starts with '/', it's an absolute path from root
    if (trimmedUrl.startsWith('/')) {
        return `${contextPath}${trimmedUrl}`;
    }
    
    // Otherwise, it's a relative path - add context path with slash
    return `${contextPath}/${trimmedUrl}`;
};

/**
 * Resolve user avatar URL with proper context path handling
 * @param {string} avatarUrl - Avatar URL from database or API
 * @returns {string} Resolved avatar URL
 */
KaruruUtils.resolveAvatarUrl = function(avatarUrl) {
    return KaruruUtils.resolveImageUrl(avatarUrl, '/img/default-avatar.png');
};

/**
 * Resolve product image URL with proper context path handling
 * @param {string} imageUrl - Product image URL from database or API
 * @returns {string} Resolved product image URL
 */
KaruruUtils.resolveProductImageUrl = function(imageUrl) {
    return KaruruUtils.resolveImageUrl(imageUrl, '/img/default-product.png');
};

/**
 * Create image element with proper error handling
 * @param {string} imageUrl - Image URL to display
 * @param {string} alt - Alt text for the image
 * @param {string} className - CSS classes for the image
 * @param {object} attributes - Additional attributes (style, onerror, etc.)
 * @returns {string} HTML string for img element
 */
KaruruUtils.createImageElement = function(imageUrl, alt = '', className = '', attributes = {}) {
    const resolvedUrl = KaruruUtils.resolveImageUrl(imageUrl);
    const defaultImage = imageUrl && imageUrl.includes('avatar') 
        ? KaruruUtils.resolveImageUrl('', '/img/default-avatar.png')
        : KaruruUtils.resolveImageUrl('', '/img/default-product.png');
    
    let attrs = '';
    Object.keys(attributes).forEach(key => {
        attrs += ` ${key}="${attributes[key]}"`;
    });
    
    // Add onerror handler if not already provided
    if (!attributes.onerror) {
        attrs += ` onerror="this.onerror=null; this.src='${defaultImage}';"`;
    }
    
    return `<img src="${resolvedUrl}" alt="${alt}" class="${className}"${attrs}>`;
};

// Initialize WebSockets (delegates to websocket.js - enabled by default)
window.initWebSockets = function() {
    const userId = window.currentUserId || parseInt(sessionStorage.getItem('userId') || '0');
    if (userId && userId > 0 && window.webSocketManager) {
        if (localStorage.getItem('websocket_enabled') === 'false') {
            window.webSocketManager.userId = userId;
            window.webSocketManager.startMessagePolling();
            window.webSocketManager.startNotificationPolling();
        } else {
            window.webSocketManager.initialize(userId);
        }
    }
};

// Initialize Mobile Bottom Navigation Active State
window.initMobileBottomNav = function() {
    const bottomNav = document.getElementById('mobileBottomNav');
    if (!bottomNav) return;
    
    // Get current page path
    const currentPath = window.location.pathname;
    const contextPath = window.CONTEXT_PATH || '';
    
    // Remove context path from current path
    let pagePath = currentPath;
    if (contextPath && pagePath.startsWith(contextPath)) {
        pagePath = pagePath.substring(contextPath.length);
    }
    
    // Normalize path (remove leading slash, get filename)
    pagePath = pagePath.replace(/^\//, '');
    if (!pagePath || pagePath === '') {
        pagePath = 'index.jsp';
    }
    
    // Map page paths to data-page values
    const pageMap = {
        'index.jsp': 'index',
        'products.jsp': 'products',
        'categories.jsp': 'categories',
        'cart.jsp': 'cart',
        'favorites.jsp': 'favorites',
        'profile.jsp': 'profile',
        'login.jsp': 'login',
        'register.jsp': 'login'
    };
    
    const currentPage = pageMap[pagePath] || pagePath.replace('.jsp', '');
    
    // Set active state
    const navItems = bottomNav.querySelectorAll('.bottom-nav-item');
    navItems.forEach(item => {
        const itemPage = item.getAttribute('data-page');
        if (itemPage === currentPage) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
};

// Ensure mobile "Other" menu modal always stays on top (esp. products page where search UI uses high z-index)
window.initMobileMoreMenuModal = function() {
    const modalEl = document.getElementById('mobileMoreMenuModal');
    if (!modalEl) return;

    // Add a specific class to the backdrop so we can raise z-index safely without impacting other modals.
    modalEl.addEventListener('shown.bs.modal', function() {
        const backdrops = document.querySelectorAll('.modal-backdrop');
        const backdrop = backdrops.length ? backdrops[backdrops.length - 1] : null;
        if (backdrop) backdrop.classList.add('mobile-more-backdrop');
    });

    // If product search suggestions or filter collapse is open, hide it to avoid visual overlap.
    modalEl.addEventListener('show.bs.modal', function() {
        const suggestions = document.getElementById('searchSuggestions') || document.getElementById('searchSuggestionsDropdown') || document.getElementById('searchSuggestionsMenu');
        if (suggestions) {
            suggestions.style.display = 'none';
            suggestions.classList.remove('show');
        }

        const filterCollapse = document.getElementById('filterCollapse');
        if (filterCollapse && filterCollapse.classList.contains('show') && window.bootstrap && bootstrap.Collapse) {
            const bsCollapse = bootstrap.Collapse.getInstance(filterCollapse) || new bootstrap.Collapse(filterCollapse, { toggle: false });
            bsCollapse.hide();
        }
    });
};

// Dismiss notifications when opening Other menu (desktop dropdown or mobile modal)
function initDismissNotificationsOnOtherMenu() {
    // Desktop: その他 dropdown
    const moreMenuDropdown = document.getElementById('moreMenuDropdown');
    if (moreMenuDropdown) {
        const dropdownParent = moreMenuDropdown.closest('.dropdown');
        if (dropdownParent) {
            dropdownParent.addEventListener('show.bs.dropdown', function() {
                if (KaruruUtils && KaruruUtils.dismissAllNotifications) {
                    KaruruUtils.dismissAllNotifications();
                }
            });
        }
    }
    // Mobile: その他 modal
    const mobileMoreMenuModal = document.getElementById('mobileMoreMenuModal');
    if (mobileMoreMenuModal) {
        mobileMoreMenuModal.addEventListener('show.bs.modal', function() {
            if (KaruruUtils && KaruruUtils.dismissAllNotifications) {
                KaruruUtils.dismissAllNotifications();
            }
        });
    }
}

// Initialize on page load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
        // Initialize mobile bottom nav
        if (window.initMobileBottomNav) {
            window.initMobileBottomNav();
        }

        if (window.initMobileMoreMenuModal) {
            window.initMobileMoreMenuModal();
        }
        
        if (window.currentUserId) {
            window.initWebSockets();
        }
        
        initDismissNotificationsOnOtherMenu();
    });
} else {
    // Initialize mobile bottom nav
    if (window.initMobileBottomNav) {
        window.initMobileBottomNav();
    }

    if (window.initMobileMoreMenuModal) {
        window.initMobileMoreMenuModal();
    }
    
    if (window.currentUserId) {
        window.initWebSockets();
    }
    
    initDismissNotificationsOnOtherMenu();
}
