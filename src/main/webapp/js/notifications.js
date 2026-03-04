// Notifications page JavaScript with WebSocket support

let currentFilter = 'all';
let currentPage = 1;
let isLoading = false;
let hasMore = true;
let allNotifications = []; // Store all loaded notifications

document.addEventListener('DOMContentLoaded', function() {
    loadNotifications();
    setupWebSocketHandlers();
    setupFilterButtons();
    setupInfiniteScroll();
    
    // Auto-refresh every 30 seconds (but only if not loading)
    setInterval(() => {
        if (!isLoading) {
            refreshNotifications();
        }
    }, 30000);
});

function setupFilterButtons() {
    const filterButtons = document.querySelectorAll('.filter-btn');
    filterButtons.forEach(btn => {
        btn.addEventListener('click', function() {
            filterButtons.forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            currentFilter = this.getAttribute('data-filter');
            loadNotifications(true); // Reset when filter changes
        });
    });
}

function setupInfiniteScroll() {
    const container = document.getElementById('notificationsList');
    if (!container) return;
    
    container.addEventListener('scroll', function() {
        if (isLoading || !hasMore) return;
        
        // Check if user scrolled near bottom (within 200px)
        const scrollTop = container.scrollTop;
        const scrollHeight = container.scrollHeight;
        const clientHeight = container.clientHeight;
        
        if (scrollTop + clientHeight >= scrollHeight - 200) {
            currentPage++;
            loadNotifications(false);
        }
    });
}

async function refreshNotifications() {
    // Refresh only first page without scrolling
    const oldPage = currentPage;
    currentPage = 1;
    await loadNotifications(true);
    currentPage = oldPage;
}

async function loadNotifications(reset = false) {
    if (isLoading) return;
    
    if (reset) {
        currentPage = 1;
        hasMore = true;
        allNotifications = [];
    }
    
    if (!hasMore && !reset) return;
    
    isLoading = true;
    const container = document.getElementById('notificationsList');
    
    try {
        const url = `${window.CONTEXT_PATH}/NotificationsServlet?action=getNotifications${currentFilter !== 'all' ? '&filter=' + currentFilter : ''}&page=${currentPage}&pageSize=20`;
        const response = await fetch(url);
        
        if (!response.ok) {
            console.error('Failed to load notifications:', response.status);
            const container = document.getElementById('notificationsList');
            if (container) {
                container.innerHTML = `
                    <div class="text-center p-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">通知の読み込みに失敗しました (${response.status})</p>
                    </div>
                `;
            }
            return;
        }
        
        const text = await response.text();
        let data = {};
        
        try {
            data = JSON.parse(text);
        } catch (e) {
            console.error('Failed to parse notifications response:', e, 'Response:', text);
            const container = document.getElementById('notificationsList');
            if (container) {
                container.innerHTML = `
                    <div class="text-center p-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">通知の読み込みに失敗しました</p>
                    </div>
                `;
            }
            return;
        }
        
        // Check for error response
        if (data.success === false || (data.error && !data.notifications && !data.data && !Array.isArray(data))) {
            console.error('Server error:', data.error || data.message);
            const container = document.getElementById('notificationsList');
            if (container) {
                container.innerHTML = `
                    <div class="text-center p-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">${escapeHtml(data.error || data.message || '通知の読み込みに失敗しました')}</p>
                    </div>
                `;
            }
            return;
        }
        
        // Extract notifications from response - handle multiple response formats
        let notifications = [];
        if (Array.isArray(data)) {
            notifications = data;
        } else if (data.notifications && Array.isArray(data.notifications)) {
            notifications = data.notifications;
        } else if (data.data && Array.isArray(data.data)) {
            notifications = data.data;
        } else if (data.items && Array.isArray(data.items)) {
            notifications = data.items;
        }
        
        hasMore = data.hasMore || false;
        
        // Append to allNotifications or replace if reset
        if (reset) {
            allNotifications = notifications;
        } else {
            allNotifications = [...allNotifications, ...notifications];
        }
        
        if (!container) return;
        
        if (allNotifications && allNotifications.length > 0) {
            let lastDate = null;
            container.innerHTML = allNotifications.map(notif => {
                // Safely parse date
                let notificationDate = null;
                if (notif.created_at) {
                    try {
                        notificationDate = new Date(notif.created_at);
                        // Check if date is valid
                        if (isNaN(notificationDate.getTime())) {
                            notificationDate = null;
                        }
                    } catch (e) {
                        console.error('Invalid date format:', notif.created_at);
                        notificationDate = null;
                    }
                }
                
                const showDateSeparator = lastDate === null || 
                    (notificationDate && notificationDate instanceof Date && 
                     (notificationDate.getDate() !== lastDate.getDate() || 
                     notificationDate.getMonth() !== lastDate.getMonth() || 
                     notificationDate.getFullYear() !== lastDate.getFullYear()));
                
                if (notificationDate && notificationDate instanceof Date) {
                    lastDate = notificationDate;
                }
                
                const formattedDate = notificationDate ? formatMessageDate(notificationDate) : '';
                const formattedTime = notif.created_at ? formatMessageTime(notif.created_at) : '';
                const isUnread = !notif.is_read || notif.is_read === 0 || notif.is_read === false;
                
                return `
                    ${showDateSeparator ? `
                        <div class="notification-date-separator">
                            <span>${formattedDate}</span>
                        </div>
                    ` : ''}
                    <div class="notification-item ${isUnread ? 'unread' : 'read'}" 
                         data-notification-id="${notif.notification_id}"
                         ${notif.action_url ? `onclick="window.location.href='${getNotificationActionUrl(notif.action_url)}'" style="cursor: pointer;"` : ''}>
                        <div class="notification-icon-wrapper">
                            <div class="notification-icon ${getNotificationIconClass(notif.type)}">
                                ${getNotificationIcon(notif.type)}
                            </div>
                            ${isUnread ? '<span class="unread-indicator"></span>' : ''}
                        </div>
                        <div class="notification-content">
                            <div class="notification-header">
                                <h6 class="notification-title mb-1">${escapeHtml(notif.title || '通知')}</h6>
                                <span class="notification-time">${formattedTime}</span>
                            </div>
                            <p class="notification-message mb-0">${escapeHtml(notif.message || '')}</p>
                        </div>
                        <div class="notification-actions" onclick="event.stopPropagation();">
                            ${isUnread ? `
                                <button class="btn btn-sm btn-outline-primary rounded-circle" 
                                        onclick="markAsRead(${notif.notification_id})" 
                                        title="既読にする">
                                    <i class="bi bi-check"></i>
                                </button>
                            ` : ''}
                            <button class="btn btn-sm btn-outline-danger rounded-circle" 
                                    onclick="deleteNotification(${notif.notification_id})" 
                                    title="削除">
                                <i class="bi bi-trash"></i>
                            </button>
                        </div>
                    </div>
                `;
            }).join('');
        } else {
            container.innerHTML = `
                <div class="text-center p-5 text-muted">
                    <div class="empty-icon mb-4">
                        <i class="bi bi-bell-slash"></i>
                    </div>
                    <h5 class="mb-2">通知がありません</h5>
                    <p class="mb-0">新しい通知が届くとここに表示されます</p>
                </div>
            `;
        }
        
        // Show load more indicator if hasMore
        if (hasMore) {
            container.innerHTML += `
                <div id="notificationsLoader" class="text-center p-3">
                    <div class="spinner-border spinner-border-sm text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            `;
        }
        
        // Update badge
        updateNotificationBadge();
        
        isLoading = false;
    } catch (error) {
        isLoading = false;
        console.error('Error loading notifications:', error);
        const container = document.getElementById('notificationsList');
        if (container) {
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">通知の読み込みに失敗しました</p>
                    <p class="text-muted small mt-2">${escapeHtml(error.message || '')}</p>
                </div>
            `;
        }
        // Show notification if available
    }
}

function getNotificationActionUrl(actionUrl) {
    if (!actionUrl) return '#';
    const contextPath = window.CONTEXT_PATH || '';
    if (actionUrl.startsWith('http') || actionUrl.startsWith('/')) {
        return actionUrl;
    }
    return (contextPath ? contextPath + '/' : '') + actionUrl.replace(/^\//, '');
}

function getNotificationIcon(type) {
    const icons = {
        'order': '<i class="bi bi-box-seam-fill"></i>',
        'rental': '<i class="bi bi-calendar-check-fill"></i>',
        'message': '<i class="bi bi-chat-dots-fill"></i>',
        'offer': '<i class="bi bi-currency-yen"></i>',
        'review': '<i class="bi bi-star-fill"></i>',
        'system': '<i class="bi bi-info-circle-fill"></i>',
        'promotion': '<i class="bi bi-gift-fill"></i>',
        'reminder': '<i class="bi bi-clock-fill"></i>'
    };
    return icons[type] || '<i class="bi bi-bell-fill"></i>';
}

function getNotificationIconClass(type) {
    const classes = {
        'order': 'icon-order',
        'rental': 'icon-rental',
        'message': 'icon-message',
        'offer': 'icon-offer',
        'review': 'icon-review',
        'system': 'icon-system',
        'promotion': 'icon-promotion',
        'reminder': 'icon-reminder'
    };
    return classes[type] || 'icon-default';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatMessageTime(timestamp) {
    if (!timestamp) return '';
    try {
        const date = new Date(timestamp);
        // Check if date is valid
        if (isNaN(date.getTime())) {
            return '';
        }
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);
        
        if (diffMins < 1) return 'たった今';
        if (diffMins < 60) return `${diffMins}分前`;
        if (diffHours < 24) return `${diffHours}時間前`;
        if (diffDays < 7) return `${diffDays}日前`;
        
        return date.toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
        console.error('Error formatting time:', e);
        return '';
    }
}

function formatMessageDate(date) {
    if (!date) return '';
    try {
        // Handle both Date objects and date strings
        const dateObj = date instanceof Date ? date : new Date(date);
        
        // Check if date is valid
        if (isNaN(dateObj.getTime())) {
            return '';
        }
        
        const today = new Date();
        const yesterday = new Date(today);
        yesterday.setDate(yesterday.getDate() - 1);
        
        if (dateObj.toDateString() === today.toDateString()) {
            return '今日';
        } else if (dateObj.toDateString() === yesterday.toDateString()) {
            return '昨日';
        } else {
            return dateObj.toLocaleDateString('ja-JP', { month: 'long', day: 'numeric' });
        }
    } catch (e) {
        console.error('Error formatting date:', e);
        return '';
    }
}

async function markAsRead(notificationId) {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/NotificationsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                action: 'markAsRead',
                notification_id: notificationId
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const text = await response.text();
        let result = {};
        try {
            result = JSON.parse(text);
        } catch (e) {
            console.error('Failed to parse markAsRead response:', e, 'Response:', text);
            throw new Error('Invalid response from server');
        }
        
        if (result.success) {
            const item = document.querySelector(`[data-notification-id="${notificationId}"]`);
            if (item) {
                item.classList.remove('unread');
                item.classList.add('read');
                const actionsDiv = item.querySelector('.notification-actions');
                if (actionsDiv) {
                    actionsDiv.innerHTML = `
                        <button class="btn btn-sm btn-outline-danger rounded-circle" 
                                onclick="deleteNotification(${notificationId})" 
                                title="削除">
                            <i class="bi bi-trash"></i>
                        </button>
                    `;
                }
                const indicator = item.querySelector('.unread-indicator');
                if (indicator) indicator.remove();
            }
            updateNotificationBadge();
            
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('既読にしました', 'success');
            }
        } else {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('既読にできませんでした', 'warning');
            }
        }
    } catch (error) {
        console.error('Error marking notification as read:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('既読にできませんでした', 'danger');
        }
    }
}

async function markAllAsRead() {
    if (!confirm('すべての通知を既読にしますか？')) {
        return;
    }
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/NotificationsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                action: 'markAllAsRead'
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const text = await response.text();
        let result = {};
        try {
            result = JSON.parse(text);
        } catch (e) {
            console.error('Failed to parse markAllAsRead response:', e, 'Response:', text);
            throw new Error('Invalid response from server');
        }
        
        if (result.success) {
            loadNotifications();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('すべて既読にしました', 'success');
            }
        } else {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('既読にできませんでした', 'warning');
            }
        }
    } catch (error) {
        console.error('Error marking all as read:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('既読にできませんでした', 'danger');
        }
    }
}

async function deleteNotification(notificationId) {
    if (!confirm('この通知を削除しますか？')) {
        return;
    }
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/NotificationsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                action: 'deleteNotification',
                notification_id: notificationId
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const text = await response.text();
        let result = {};
        try {
            result = JSON.parse(text);
        } catch (e) {
            console.error('Failed to parse deleteNotification response:', e, 'Response:', text);
            throw new Error('Invalid response from server');
        }
        
        if (result.success) {
            const item = document.querySelector(`[data-notification-id="${notificationId}"]`);
            if (item) {
                item.style.opacity = '0';
                item.style.transform = 'translateX(-20px)';
                item.style.transition = 'all 0.3s ease-out';
                setTimeout(() => {
                    item.remove();
                    // Check if list is empty
                    const container = document.getElementById('notificationsList');
                    if (container && container.querySelectorAll('.notification-item').length === 0) {
                        container.innerHTML = `
                            <div class="text-center p-5 text-muted">
                                <div class="empty-icon mb-4">
                                    <i class="bi bi-bell-slash"></i>
                                </div>
                                <h5 class="mb-2">通知がありません</h5>
                                <p class="mb-0">新しい通知が届くとここに表示されます</p>
                            </div>
                        `;
                    }
                }, 300);
            }
            updateNotificationBadge();
            
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('通知を削除しました', 'success');
            }
        } else {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('削除できませんでした', 'warning');
            }
        }
    } catch (error) {
        console.error('Error deleting notification:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('削除できませんでした', 'danger');
        }
    }
}

function setupWebSocketHandlers() {
    // Expose refreshNotifications for WebSocket/callbacks (use direct reference to avoid recursion)
    const doRefresh = refreshNotifications;
    window.refreshNotifications = function() {
        doRefresh();
    };
    
    // Add new notification to top of list in real-time
    window.addNotification = function(notification) {
        const container = document.getElementById('notificationsList');
        if (!container) return;
        
        // Check if notification already exists
        const existing = container.querySelector(`[data-notification-id="${notification.notification_id}"]`);
        if (existing) return;
        
        // Add to beginning of allNotifications
        allNotifications.unshift(notification);
        
        // Reload display
        loadNotifications(true);
        
        // Update badge
        updateNotificationBadge();
    };
    
    // Use main.js updateNotificationBadge (updates header, dropdown, mobile badges)
    // Do NOT overwrite - main.js version updates all badge locations correctly
}

async function updateNotificationBadge() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/NotificationsServlet?action=getUnreadCount`);
        if (response.ok) {
            const text = await response.text();
            let data = {};
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse badge response:', e);
                return;
            }
            
            // Handle both success and non-success responses
            if (data.success !== false) {
                const count = data.count || data.unread_count || data.unreadCount || 0;
                if (window.updateNotificationBadge) {
                    window.updateNotificationBadge(count);
                }
            } else {
                // If error, set count to 0
                if (window.updateNotificationBadge) {
                    window.updateNotificationBadge(0);
                }
            }
        } else {
            console.error('Failed to get unread count:', response.status);
            // Set count to 0 on error
            if (window.updateNotificationBadge) {
                window.updateNotificationBadge(0);
            }
        }
    } catch (error) {
        console.error('Error updating notification badge:', error);
        // Set count to 0 on error
        if (window.updateNotificationBadge) {
            window.updateNotificationBadge(0);
        }
    }
}
