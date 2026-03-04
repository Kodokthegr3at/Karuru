// WebSocket Manager for Messages and Notifications

class WebSocketManager {
    constructor() {
        this.messageSocket = null;
        this.notificationSocket = null;
        this.userId = null;
        this.messageReconnectAttempts = 0;
        this.notificationReconnectAttempts = 0;
        this.maxReconnectAttempts = 3; // Reduced to 3 attempts
        this.reconnectDelay = 3000;
        this.messagePollingInterval = null;
        this.notificationPollingInterval = null;
        this.pollingInterval = 10000; // Poll every 10 seconds
        this.messageSocketConnected = false;
        this.notificationSocketConnected = false;
    }
    
    initialize(userId) {
        if (!userId) {
            console.warn('WebSocket: User ID not provided');
            return;
        }
        
        // Check if WebSocket is enabled via localStorage (default: enabled)
        // To disable WebSocket, run: localStorage.setItem('websocket_enabled', 'false')
        if (localStorage.getItem('websocket_enabled') === 'false') {
            console.log('WebSocket: Disabled via localStorage, using polling only');
            this.userId = userId;
            this.startMessagePolling();
            this.startNotificationPolling();
            return;
        }
        
        // Reset attempts when initializing
        this.messageReconnectAttempts = 0;
        this.notificationReconnectAttempts = 0;
        this.messageSocketConnected = false;
        this.notificationSocketConnected = false;
        
        this.userId = userId;
        this.connectMessageSocket();
        this.connectNotificationSocket();
    }
    
    connectMessageSocket() {
        if (!this.userId) return;
        
        // Prevent multiple connection attempts
        if (this.messageSocket && (this.messageSocket.readyState === WebSocket.CONNECTING || this.messageSocket.readyState === WebSocket.OPEN)) {
            return;
        }
        
        // If max attempts reached, use polling instead
        if (this.messageReconnectAttempts >= this.maxReconnectAttempts) {
            if (!this.messagePollingInterval) {
                console.log('Message WebSocket: Max attempts reached, using polling');
                this.startMessagePolling();
            }
            return;
        }
        
        // Check if WebSocket is supported
        if (!window.WebSocket) {
            console.warn('WebSocket is not supported in this browser');
            this.startMessagePolling();
            return;
        }
        
        try {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const host = window.location.host;
            const contextPath = window.CONTEXT_PATH || '';
            // Server expects userId parameter
            const wsUrl = `${protocol}//${host}${contextPath}/message-websocket?userId=${this.userId}`;
            
            this.messageSocket = new WebSocket(wsUrl);
            
            // Set timeout to detect connection failure
            const connectionTimeout = setTimeout(() => {
                if (this.messageSocket && this.messageSocket.readyState !== WebSocket.OPEN) {
                    if (this.messageSocket) {
                        try {
                            this.messageSocket.close();
                        } catch (e) {
                            // Ignore close errors
                        }
                    }
                    this.messageSocket = null;
                    this.messageReconnectAttempts++;
                    if (this.messageReconnectAttempts >= this.maxReconnectAttempts) {
                        console.log('Message WebSocket: Connection timeout, max attempts reached - using polling');
                        this.startMessagePolling();
                    } else {
                        console.debug(`Message WebSocket: Connection timeout, will retry (${this.messageReconnectAttempts}/${this.maxReconnectAttempts})`);
                    }
                }
            }, 5000);
            
            this.messageSocket.onopen = () => {
                clearTimeout(connectionTimeout);
                console.log('Message WebSocket connected');
                this.messageReconnectAttempts = 0;
                this.messageSocketConnected = true;
                this.stopMessagePolling(); // Stop polling if WebSocket connects
                this.onMessageSocketOpen();
            };
            
            this.messageSocket.onmessage = (event) => {
                this.handleMessage(event.data);
            };
            
            this.messageSocket.onerror = (error) => {
                clearTimeout(connectionTimeout);
                // Don't log error if we're already using polling
                if (this.messageReconnectAttempts < this.maxReconnectAttempts) {
                    console.debug('Message WebSocket error (will retry):', error);
                } else {
                    console.debug('Message WebSocket error (using polling):', error);
                }
                this.onMessageSocketError(error);
                // Increment attempts on error
                if (this.messageReconnectAttempts < this.maxReconnectAttempts) {
                    this.messageReconnectAttempts++;
                }
                // Fallback to polling if max attempts reached
                if (this.messageReconnectAttempts >= this.maxReconnectAttempts && !this.messagePollingInterval) {
                    this.startMessagePolling();
                }
            };
            
            this.messageSocket.onclose = () => {
                clearTimeout(connectionTimeout);
                this.messageSocketConnected = false;
                this.messageSocket = null; // Clear reference
                this.onMessageSocketClose();
                // Only reconnect if not intentionally closed and haven't exceeded max attempts
                if (this.messageReconnectAttempts < this.maxReconnectAttempts) {
                    this.reconnectMessageSocket();
                } else {
                    // Fallback to polling after max reconnect attempts
                    if (!this.messagePollingInterval) {
                        console.log('Message WebSocket: Max attempts reached, using polling');
                        this.startMessagePolling();
                    }
                }
            };
        } catch (error) {
            this.messageReconnectAttempts++;
            if (this.messageReconnectAttempts >= this.maxReconnectAttempts) {
                console.log('Message WebSocket: Failed to create, max attempts reached - using polling');
                this.startMessagePolling();
            } else {
                console.log(`Message WebSocket: Failed to create, will retry (${this.messageReconnectAttempts}/${this.maxReconnectAttempts})`);
                setTimeout(() => this.connectMessageSocket(), this.reconnectDelay);
            }
        }
    }
    
    connectNotificationSocket() {
        if (!this.userId) return;
        
        // Prevent multiple connection attempts
        if (this.notificationSocket && (this.notificationSocket.readyState === WebSocket.CONNECTING || this.notificationSocket.readyState === WebSocket.OPEN)) {
            return;
        }
        
        // If max attempts reached, use polling instead
        if (this.notificationReconnectAttempts >= this.maxReconnectAttempts) {
            if (!this.notificationPollingInterval) {
                console.log('Notification WebSocket: Max attempts reached, using polling');
                this.startNotificationPolling();
            }
            return;
        }
        
        // Check if WebSocket is supported
        if (!window.WebSocket) {
            console.warn('WebSocket is not supported in this browser');
            this.startNotificationPolling();
            return;
        }
        
        try {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const host = window.location.host;
            const contextPath = window.CONTEXT_PATH || '';
            // Server expects userId parameter
            const wsUrl = `${protocol}//${host}${contextPath}/notification-websocket?userId=${this.userId}`;
            
            this.notificationSocket = new WebSocket(wsUrl);
            
            // Set timeout to detect connection failure
            const connectionTimeout = setTimeout(() => {
                if (this.notificationSocket && this.notificationSocket.readyState !== WebSocket.OPEN) {
                    if (this.notificationSocket) {
                        try {
                            this.notificationSocket.close();
                        } catch (e) {
                            // Ignore close errors
                        }
                    }
                    this.notificationSocket = null;
                    this.notificationReconnectAttempts++;
                    if (this.notificationReconnectAttempts >= this.maxReconnectAttempts) {
                        console.log('Notification WebSocket: Connection timeout, max attempts reached - using polling');
                        this.startNotificationPolling();
                    } else {
                        console.debug(`Notification WebSocket: Connection timeout, will retry (${this.notificationReconnectAttempts}/${this.maxReconnectAttempts})`);
                    }
                }
            }, 5000);
            
            this.notificationSocket.onopen = () => {
                clearTimeout(connectionTimeout);
                console.log('Notification WebSocket connected');
                this.notificationReconnectAttempts = 0;
                this.notificationSocketConnected = true;
                this.stopNotificationPolling(); // Stop polling if WebSocket connects
                this.onNotificationSocketOpen();
            };
            
            this.notificationSocket.onmessage = (event) => {
                this.handleNotification(event.data);
            };
            
            this.notificationSocket.onerror = (error) => {
                clearTimeout(connectionTimeout);
                // Don't log error if we're already using polling
                if (this.notificationReconnectAttempts < this.maxReconnectAttempts) {
                    console.debug('Notification WebSocket error (will retry):', error);
                } else {
                    console.debug('Notification WebSocket error (using polling):', error);
                }
                this.onNotificationSocketError(error);
                // Increment attempts on error
                if (this.notificationReconnectAttempts < this.maxReconnectAttempts) {
                    this.notificationReconnectAttempts++;
                }
                // Fallback to polling if max attempts reached
                if (this.notificationReconnectAttempts >= this.maxReconnectAttempts && !this.notificationPollingInterval) {
                    this.startNotificationPolling();
                }
            };
            
            this.notificationSocket.onclose = () => {
                clearTimeout(connectionTimeout);
                this.notificationSocketConnected = false;
                this.notificationSocket = null; // Clear reference
                this.onNotificationSocketClose();
                // Only reconnect if not intentionally closed and haven't exceeded max attempts
                if (this.notificationReconnectAttempts < this.maxReconnectAttempts) {
                    this.reconnectNotificationSocket();
                } else {
                    // Fallback to polling after max reconnect attempts
                    if (!this.notificationPollingInterval) {
                        console.log('Notification WebSocket: Max attempts reached, using polling');
                        this.startNotificationPolling();
                    }
                }
            };
        } catch (error) {
            this.notificationReconnectAttempts++;
            if (this.notificationReconnectAttempts >= this.maxReconnectAttempts) {
                console.log('Notification WebSocket: Failed to create, max attempts reached - using polling');
                this.startNotificationPolling();
            } else {
                console.log(`Notification WebSocket: Failed to create, will retry (${this.notificationReconnectAttempts}/${this.maxReconnectAttempts})`);
                setTimeout(() => this.connectNotificationSocket(), this.reconnectDelay);
            }
        }
    }
    
    reconnectMessageSocket() {
        // Check if already at max attempts
        if (this.messageReconnectAttempts >= this.maxReconnectAttempts) {
            if (!this.messagePollingInterval) {
                console.log('Message WebSocket: Max attempts reached, using polling');
                this.startMessagePolling();
            }
            return;
        }
        
        // Increment attempts BEFORE checking
        this.messageReconnectAttempts++;
        
        // Check again after increment
        if (this.messageReconnectAttempts > this.maxReconnectAttempts) {
            if (!this.messagePollingInterval) {
                console.log('Message WebSocket: Max attempts exceeded, using polling');
                this.startMessagePolling();
            }
            return;
        }
        
        console.debug(`Message WebSocket: Reconnecting (${this.messageReconnectAttempts}/${this.maxReconnectAttempts})...`);
        
        setTimeout(() => {
            // Double check before connecting
            if (!this.messageSocketConnected && 
                this.messageReconnectAttempts <= this.maxReconnectAttempts &&
                !this.messagePollingInterval) {
                this.connectMessageSocket();
            }
        }, this.reconnectDelay);
    }
    
    reconnectNotificationSocket() {
        // Check if already at max attempts
        if (this.notificationReconnectAttempts >= this.maxReconnectAttempts) {
            if (!this.notificationPollingInterval) {
                console.log('Notification WebSocket: Max attempts reached, using polling');
                this.startNotificationPolling();
            }
            return;
        }
        
        // Increment attempts BEFORE checking
        this.notificationReconnectAttempts++;
        
        // Check again after increment
        if (this.notificationReconnectAttempts > this.maxReconnectAttempts) {
            if (!this.notificationPollingInterval) {
                console.log('Notification WebSocket: Max attempts exceeded, using polling');
                this.startNotificationPolling();
            }
            return;
        }
        
        console.debug(`Notification WebSocket: Reconnecting (${this.notificationReconnectAttempts}/${this.maxReconnectAttempts})...`);
        
        setTimeout(() => {
            // Double check before connecting
            if (!this.notificationSocketConnected && 
                this.notificationReconnectAttempts <= this.maxReconnectAttempts &&
                !this.notificationPollingInterval) {
                this.connectNotificationSocket();
            }
        }, this.reconnectDelay);
    }
    
    handleMessage(data) {
        try {
            const message = JSON.parse(data);
            
            // Handle pong responses silently
            if (message.type === 'pong') {
                return;
            }
            
            console.log('Message received:', message);
            
            switch(message.type) {
                case 'new_message':
                    this.onNewMessage(message);
                    break;
                case 'typing':
                    this.onTyping(message);
                    break;
                case 'message_read':
                    this.onMessageRead(message);
                    break;
                case 'messages_read':
                    this.onMessagesRead(message);
                    break;
                default:
                    console.log('Unknown message type:', message.type);
            }
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    }
    
    handleNotification(data) {
        try {
            const notification = JSON.parse(data);
            
            // Handle pong responses silently
            if (notification.type === 'pong' || notification.event === 'pong') {
                return;
            }
            
            console.log('Notification received:', notification);
            
            switch(notification.type || notification.event) {
                case 'notification':
                case 'new_notification':
                    this.onNewNotification(notification);
                    break;
                case 'order_update':
                    this.onOrderUpdate(notification);
                    break;
                case 'review_approved':
                    this.onReviewApproved(notification);
                    break;
                default:
                    console.log('Unknown notification type:', notification.type || notification.event);
            }
        } catch (error) {
            console.error('Error parsing notification:', error);
        }
    }
    
    onNewMessage(message) {
        // Update messages list if on messages page (real-time)
        if (typeof window.updateMessagesList === 'function') {
            window.updateMessagesList(message);
        }
        
        // Show notification
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('新しいメッセージが届きました', 'info');
        }
        
        // Update unread count and header badge (works on all pages)
        this.updateUnreadCount();
    }
    
    onNewNotification(notification) {
        const data = notification.data || notification;
        const title = data.title || '新しい通知';
        const message = data.message || '';
        
        // Show browser notification if permission granted
        if ('Notification' in window && Notification.permission === 'granted') {
            try {
                new Notification(title, {
                    body: message,
                    icon: (window.CONTEXT_PATH || '') + '/img/icon.png'
                });
            } catch (e) { /* ignore */ }
        }
        
        // Show in-app notification
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(message || title, 'info');
        }
        
        // Update notification badge (header - works on all pages)
        this.updateNotificationBadge();
        
        // Refresh notifications list if on notifications page (real-time)
        if (typeof window.refreshNotifications === 'function') {
            window.refreshNotifications();
        }
    }
    
    onTyping(message) {
        // Only show typing indicator if user is actually typing
        if (message.typing === true || message.isTyping === true) {
            // Show typing indicator
            if (typeof window.showTypingIndicator === 'function') {
                const senderId = message.sender_id || message.senderId;
                const senderName = message.sender_name || message.senderName || 'ユーザー';
                window.showTypingIndicator(senderId, senderName);
            }
        } else {
            // Hide typing indicator
            const typingIndicator = document.getElementById('typingIndicator');
            if (typingIndicator) {
                typingIndicator.remove();
            }
        }
    }
    
    onMessageRead(message) {
        // Update single message read status
        if (typeof window.updateMessageReadStatus === 'function') {
            window.updateMessageReadStatus(message.message_id);
        }
    }
    
    onMessagesRead(message) {
        // Update all sent messages as read when receiver views chat (real-time 既読)
        if (typeof window.updateMessagesReadStatus === 'function') {
            window.updateMessagesReadStatus(message.receiver_id, message.product_id);
        }
    }
    
    onOrderUpdate(notification) {
        KaruruUtils.showNotification('注文の状態が更新されました', 'info');
        
        // Refresh orders if on orders page
        if (typeof window.refreshOrders === 'function') {
            window.refreshOrders();
        }
    }
    
    onReviewApproved(notification) {
    }
    
    onMessageSocketOpen() {
        // Send ping to keep connection alive
        this.startMessagePing();
    }
    
    onNotificationSocketOpen() {
        // Send ping to keep connection alive
        this.startNotificationPing();
    }
    
    onMessageSocketError(error) {
        // Error is already handled in connectMessageSocket with fallback
        // This is just for logging
        console.debug('Message WebSocket error (fallback to polling):', error);
    }
    
    onNotificationSocketError(error) {
        // Error is already handled in connectNotificationSocket with fallback
        // This is just for logging
        console.debug('Notification WebSocket error (fallback to polling):', error);
    }
    
    onMessageSocketClose() {
        this.stopMessagePing();
    }
    
    onNotificationSocketClose() {
        this.stopNotificationPing();
    }
    
    startMessagePing() {
        if (this.messagePingInterval) {
            clearInterval(this.messagePingInterval);
        }
        
        this.messagePingInterval = setInterval(() => {
            if (this.messageSocket && this.messageSocket.readyState === WebSocket.OPEN) {
                this.messageSocket.send(JSON.stringify({ type: 'ping' }));
            }
        }, 30000); // Ping every 30 seconds
    }
    
    stopMessagePing() {
        if (this.messagePingInterval) {
            clearInterval(this.messagePingInterval);
            this.messagePingInterval = null;
        }
    }
    
    startNotificationPing() {
        if (this.notificationPingInterval) {
            clearInterval(this.notificationPingInterval);
        }
        
        this.notificationPingInterval = setInterval(() => {
            if (this.notificationSocket && this.notificationSocket.readyState === WebSocket.OPEN) {
                this.notificationSocket.send(JSON.stringify({ type: 'ping' }));
            }
        }, 30000); // Ping every 30 seconds
    }
    
    stopNotificationPing() {
        if (this.notificationPingInterval) {
            clearInterval(this.notificationPingInterval);
            this.notificationPingInterval = null;
        }
    }
    
    sendMessage(type, data) {
        if (this.messageSocket && this.messageSocket.readyState === WebSocket.OPEN) {
            this.messageSocket.send(JSON.stringify({ type, ...data }));
        }
    }
    
    sendTypingIndicator(receiverId, isTyping) {
        this.sendMessage('typing', {
            receiver_id: receiverId,
            typing: isTyping
        });
    }
    
    updateUnreadCount() {
        // Fetch unread count and update all message badges
        fetch(`${window.CONTEXT_PATH}/MessagesServlet?action=getUnreadCount`)
            .then(response => response.json())
            .then(data => {
                const count = data.count || data.unread_count || 0;
                if (data.success !== false) {
                    if (typeof window.updateUnreadBadge === 'function') {
                        window.updateUnreadBadge(count);
                    }
                    // Update header badge (main.js - works on all pages)
                    if (typeof window.updateMessageBadge === 'function') {
                        window.updateMessageBadge();
                    }
                }
            })
            .catch(error => console.error('Error fetching unread count:', error));
    }
    
    updateNotificationBadge() {
        // Fetch unread notifications count
        fetch(`${window.CONTEXT_PATH}/NotificationsServlet?action=getUnreadCount`)
            .then(response => response.json())
            .then(data => {
                if (data.success && typeof window.updateNotificationBadge === 'function') {
                    window.updateNotificationBadge(data.count || 0);
                }
            })
            .catch(error => console.error('Error fetching notification count:', error));
    }
    
    // Polling fallback methods
    startMessagePolling() {
        if (this.messagePollingInterval) {
            return; // Already polling
        }
        
        // Close WebSocket if still open
        if (this.messageSocket) {
            try {
                this.messageSocket.close();
            } catch (e) {
                // Ignore
            }
            this.messageSocket = null;
        }
        
        console.log('Message: Using HTTP polling (WebSocket unavailable)');
        
        // Poll immediately
        this.pollMessages();
        
        // Then poll every interval
        this.messagePollingInterval = setInterval(() => {
            this.pollMessages();
        }, this.pollingInterval);
    }
    
    stopMessagePolling() {
        if (this.messagePollingInterval) {
            clearInterval(this.messagePollingInterval);
            this.messagePollingInterval = null;
        }
    }
    
    pollMessages() {
        if (!this.userId) return;
        
        fetch(`${window.CONTEXT_PATH}/MessagesServlet?action=getUnreadCount`)
            .then(response => response.json())
            .then(data => {
                const unreadCount = data.count || data.unread_count || 0;
                if (unreadCount > 0) {
                    // Update badge
                    if (typeof window.updateMessageBadge === 'function') {
                        window.updateMessageBadge(unreadCount);
                    }
                    
                    // If on messages page, refresh
                    if (typeof window.refreshMessages === 'function') {
                        window.refreshMessages();
                    }
                }
            })
            .catch(error => {
                // Silently fail - polling will retry
                console.debug('Message polling error:', error);
            });
    }
    
    startNotificationPolling() {
        if (this.notificationPollingInterval) {
            return; // Already polling
        }
        
        // Close WebSocket if still open
        if (this.notificationSocket) {
            try {
                this.notificationSocket.close();
            } catch (e) {
                // Ignore
            }
            this.notificationSocket = null;
        }
        
        console.log('Notification: Using HTTP polling (WebSocket unavailable)');
        
        // Poll immediately
        this.pollNotifications();
        
        // Then poll every interval
        this.notificationPollingInterval = setInterval(() => {
            this.pollNotifications();
        }, this.pollingInterval);
    }
    
    stopNotificationPolling() {
        if (this.notificationPollingInterval) {
            clearInterval(this.notificationPollingInterval);
            this.notificationPollingInterval = null;
        }
    }
    
    pollNotifications() {
        if (!this.userId) return;
        
        fetch(`${window.CONTEXT_PATH}/NotificationsServlet?action=getUnreadCount`)
            .then(response => response.json())
            .then(data => {
                const unreadCount = data.count || data.unread_count || 0;
                if (unreadCount > 0) {
                    // Update badge
                    if (typeof window.updateNotificationBadge === 'function') {
                        window.updateNotificationBadge(unreadCount);
                    }
                    
                    // If on notifications page, refresh
                    if (typeof window.refreshNotifications === 'function') {
                        window.refreshNotifications();
                    }
                }
            })
            .catch(error => {
                // Silently fail - polling will retry
                console.debug('Notification polling error:', error);
            });
    }
    
    disconnect() {
        if (this.messageSocket) {
            this.messageSocket.close();
            this.messageSocket = null;
        }
        
        if (this.notificationSocket) {
            this.notificationSocket.close();
            this.notificationSocket = null;
        }
        
        this.stopMessagePing();
        this.stopNotificationPing();
        this.stopMessagePolling();
        this.stopNotificationPolling();
    }
}

// Global WebSocket Manager instance
window.webSocketManager = new WebSocketManager();

    // Initialize WebSockets when page loads (if user is logged in)
window.initWebSockets = function() {
    // Get user ID from session or page
    const userId = getUserIdFromPage();
    if (userId) {
        // Check if WebSocket is enabled (default: enabled)
        if (localStorage.getItem('websocket_enabled') === 'false') {
            console.log('WebSocket: Disabled via localStorage, using polling only');
            if (window.webSocketManager) {
                window.webSocketManager.userId = userId;
                window.webSocketManager.startMessagePolling();
                window.webSocketManager.startNotificationPolling();
            }
        } else {
            window.webSocketManager.initialize(userId);
        }
        
        // Request notification permission
        if ('Notification' in window && Notification.permission === 'default') {
            Notification.requestPermission();
        }
    }
};

function getUserIdFromPage() {
    // Try to get from global variable
    if (window.currentUserId) {
        return window.currentUserId;
    }
    
    // Try to get from sessionStorage
    if (sessionStorage.getItem('userId')) {
        return parseInt(sessionStorage.getItem('userId'));
    }
    
    // Try to get from meta tag
    const metaUserId = document.querySelector('meta[name="user-id"]');
    if (metaUserId) {
        return parseInt(metaUserId.content);
    }
    
    return null;
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // Wait a bit for session to be established
    setTimeout(() => {
        const userId = getUserIdFromPage();
        if (userId) {
            // Check if WebSocket should be disabled (default: enabled)
            // To disable WebSocket, run: localStorage.setItem('websocket_enabled', 'false')
            if (localStorage.getItem('websocket_enabled') === 'false') {
                console.log('WebSocket: Using HTTP polling (WebSocket disabled via localStorage)');
                if (window.webSocketManager) {
                    window.webSocketManager.userId = userId;
                    window.webSocketManager.startMessagePolling();
                    window.webSocketManager.startNotificationPolling();
                }
            } else {
                window.webSocketManager.initialize(userId);
            }
        } else {
            console.debug('WebSocket: User not logged in, skipping');
        }
    }, 300);
});

// Cleanup on page unload
window.addEventListener('beforeunload', function() {
    if (window.webSocketManager) {
        window.webSocketManager.disconnect();
    }
});

