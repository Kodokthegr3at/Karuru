// Messages page JavaScript

let currentConversationId = null;
let currentProductId = null; // Store current product ID for messages
let typingTimeout = null;
let isTyping = false;
let currentConversationsList = []; // Store conversations list for auto-select

document.addEventListener('DOMContentLoaded', function() {
    // Move emoji modal to body for correct positioning (avoids parent overflow/transform issues)
    const emojiModalEl = document.getElementById('emojiPickerModal');
    if (emojiModalEl && emojiModalEl.parentElement !== document.body) {
        document.body.appendChild(emojiModalEl);
    }
    // Check URL parameters for user_id, product_id, type, and reset
    const urlParams = new URLSearchParams(window.location.search);
    const userIdParam = urlParams.get('user_id');
    const productIdParam = urlParams.get('product_id');
    const messageType = urlParams.get('type'); // 'order' or 'product' or null
    const orderIdParam = urlParams.get('order_id');
    const resetParam = urlParams.get('reset');
    
    // reset=1: kembali dari close chat product detail, hapus semua konteks ask seller
    if (resetParam === '1') {
        window._skipAutoSelect = true;
        currentConversationId = null;
        currentProductId = null;
        window._openedFromProductDetail = false;
        window._wasExistingConversation = false;
        history.replaceState(null, '', (window.CONTEXT_PATH || '') + '/messages.jsp');
    }
    
    // Store URL params for later use after conversations load (skip if reset)
    window.pendingConversationUserId = (resetParam !== '1' && userIdParam) ? parseInt(userIdParam) : null;
    window.pendingConversationProductId = (resetParam !== '1' && productIdParam) ? parseInt(productIdParam) : null;
    window.pendingMessageType = messageType; // 'order' or 'product' or null
    window.pendingOrderId = orderIdParam ? parseInt(orderIdParam) : null;
    
    // Mobile: apply conversation-open layout from start when user_id in URL (prevents blank/flash before messages load)
    if (window.pendingConversationUserId && window.innerWidth <= 768) {
        const noConv = document.getElementById('noConversation');
        const convView = document.getElementById('conversationView');
        const sidebar = document.querySelector('.messages-sidebar');
        const row = document.querySelector('.messages-row');
        if (noConv) { noConv.classList.add('d-none'); noConv.style.setProperty('display', 'none', 'important'); }
        if (convView) { convView.classList.remove('d-none'); convView.style.removeProperty('display'); }
        if (sidebar) sidebar.style.display = 'none';
        if (row) row.classList.add('conversation-open-mobile');
    }
    
    // Handle window resize for mobile/desktop toggle
    window.addEventListener('resize', function() {
        const titleEl = document.getElementById('conversationTitle');
        const conversationView = document.getElementById('conversationView');
        const sidebar = document.querySelector('.messages-sidebar');
        
        if (titleEl && conversationView && !conversationView.classList.contains('d-none')) {
            const isMobile = window.innerWidth <= 768;
            const userName = (window._currentConversationUserName || titleEl.querySelector('.conversation-title-text')?.textContent || titleEl.textContent.trim().replace('会話相手', '').trim()).trim();
            const avatarUrl = window._currentConversationAvatarUrl || KaruruUtils.resolveAvatarUrl('');
            const defaultAvatar = KaruruUtils.resolveAvatarUrl('');
            
            if (userName && userName !== '') {
                const row = document.querySelector('.messages-row');
                const safeName = escapeHtml(userName);
                if (isMobile) {
                    titleEl.innerHTML = `
                        <button class="btn btn-sm btn-outline-dark messages-back-btn me-2" onclick="closeConversation()" title="戻る">
                            <i class="bi bi-arrow-left"></i>
                        </button>
                        <div class="conversation-header-avatar me-2">
                            <img src="${avatarUrl}" alt="${safeName}" class="conversation-header-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">
                        </div>
                        <span class="conversation-title-text">${safeName}</span>
                    `;
                    if (sidebar) sidebar.style.display = 'none';
                    row?.classList.add('conversation-open-mobile');
                } else {
                    titleEl.innerHTML = `
                        <div class="conversation-header-avatar me-2 d-inline-flex">
                            <img src="${avatarUrl}" alt="${safeName}" class="conversation-header-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">
                        </div>
                        <span>${safeName}</span>
                    `;
                    if (sidebar) sidebar.style.display = 'block';
                    row?.classList.remove('conversation-open-mobile');
                }
            }
        }
    });
    
    loadConversations();
    
    // Reset UI jika dari close ask seller - berlaku untuk PC dan mobile
    if (resetParam === '1') {
        function applyResetUI() {
            const noConv = document.getElementById('noConversation');
            const convView = document.getElementById('conversationView');
            if (noConv) { noConv.classList.remove('d-none'); noConv.style.removeProperty('display'); }
            if (convView) { convView.classList.add('d-none'); convView.style.setProperty('display', 'none', 'important'); }
            document.querySelectorAll('.conversation-item')?.forEach(el => el.classList.remove('active'));
            // Mobile: tampilkan sidebar, hapus layout fullscreen chat
            const sidebar = document.querySelector('.messages-sidebar');
            if (sidebar && window.innerWidth <= 768) sidebar.style.display = 'block';
            document.querySelector('.messages-row')?.classList.remove('conversation-open-mobile');
        }
        applyResetUI();
        setTimeout(applyResetUI, 400); // Jalankan lagi setelah list selesai di-render (mobile)
    }
    
    const messageForm = document.getElementById('messageForm');
    const messageInput = document.getElementById('messageText');
    
    if (messageForm) {
        messageForm.addEventListener('submit', handleSendMessage);
    }
    
    // Typing indicator
    if (messageInput) {
        messageInput.addEventListener('input', handleTyping);
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSendMessage(e);
            }
        });
    }
    
    // Attachment button
    const attachmentBtn = document.querySelector('.attachment-btn');
    const attachmentInput = document.getElementById('attachmentInput');
    if (attachmentBtn && attachmentInput) {
        attachmentBtn.addEventListener('click', function() {
            attachmentInput.click();
        });
        attachmentInput.addEventListener('change', handleAttachmentSelect);
    }
    
    // Emoji/Sticker button
    const emojiBtn = document.querySelector('.emoji-btn');
    if (emojiBtn) {
        emojiBtn.addEventListener('click', function() {
            const modalElement = document.getElementById('emojiPickerModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
                modal.show();
            }
        });
    }
    
    // Ensure emoji modal close button works (fallback for PC/mobile)
    const emojiModal = document.getElementById('emojiPickerModal');
    if (emojiModal) {
        const closeBtn = emojiModal.querySelector('.btn-close[data-bs-dismiss="modal"]');
        if (closeBtn) {
            closeBtn.addEventListener('click', function() {
                const m = bootstrap.Modal.getInstance(emojiModal);
                if (m) m.hide();
            });
        }
    }
    
    // Emoji picker items - use event delegation for dynamically loaded content
    document.addEventListener('click', function(e) {
        if (e.target.classList.contains('emoji-item')) {
            const emoji = e.target.textContent;
            if (messageInput) {
                messageInput.value += emoji;
                messageInput.focus();
            }
            const modalElement = document.getElementById('emojiPickerModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement);
                if (modal) modal.hide();
            }
        }
        
        if (e.target.classList.contains('sticker-item')) {
            const sticker = e.target.textContent;
            if (messageInput) {
                messageInput.value += sticker;
                messageInput.focus();
            }
            const modalElement = document.getElementById('emojiPickerModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement);
                if (modal) modal.hide();
            }
        }
    });
    
    // Setup WebSocket handlers
    setupWebSocketHandlers();
});

async function loadConversations() {
    const container = document.getElementById('conversationsList');
    if (!container) {
        console.error('Conversations container not found');
        return;
    }
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const response = await fetch(`${contextPath}/MessagesServlet?action=getConversations`);
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Failed to load conversations:', response.status, errorText);
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">会話の読み込みに失敗しました (${response.status})</p>
                    <small class="text-muted d-block mt-2">${errorText.substring(0, 100)}</small>
                </div>
            `;
            return;
        }
        
        const contentType = response.headers.get('content-type');
        let data = {};
        let responseText = '';
        
        try {
            if (contentType && contentType.includes('application/json')) {
                data = await response.json();
            } else {
                responseText = await response.text();
                console.log('Response text:', responseText.substring(0, 200));
                data = JSON.parse(responseText);
            }
        } catch (parseError) {
            console.error('Failed to parse conversations response:', parseError);
            if (!responseText) {
                try {
                    responseText = await response.text();
                } catch (e) {
                    responseText = 'Unable to read response';
                }
            }
            console.error('Response text:', responseText);
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">会話の読み込みに失敗しました</p>
                    <small class="text-muted d-block mt-2">JSON解析エラー: ${parseError.message}</small>
                    <small class="text-muted d-block mt-1" style="font-size: 0.7rem;">${responseText.substring(0, 200)}</small>
                </div>
            `;
            return;
        }
        
        console.log('Conversations data received:', data);
        const conversations = data.conversations || data.data || (Array.isArray(data) ? data : []);
        
        // Store conversations for filtering
        currentConversationsList = conversations;
        
        displayConversations(conversations);
        
        // Auto-select conversation: URL params take priority, else auto-select last chat (PC & mobile, only on initial load)
        let userIdToSelect = window.pendingConversationUserId;
        let productIdToSelect = window.pendingConversationProductId;
        
        // Only auto-select on initial load - skip if returning from product detail close (reset=1)
        const isInitialLoad = !currentConversationId && !window._messagesAutoSelectDone;
        const skipAutoSelect = window._skipAutoSelect === true;
        if (!userIdToSelect && conversations.length > 0 && isInitialLoad && !skipAutoSelect) {
            window._messagesAutoSelectDone = true;
            const firstConv = conversations[0];
            userIdToSelect = firstConv.other_user_id ?? firstConv.user_id;
            productIdToSelect = productIdToSelect || null;
        }
        
        if (userIdToSelect) {
            const userId = userIdToSelect;
            const messageType = window.pendingMessageType; // 'order' or 'product' or null
            
            // Find conversation or use default name
            const conv = currentConversationsList.find(c => {
                const otherId = c.other_user_id || c.user_id;
                return parseInt(otherId) === parseInt(userId);
            });
            
            // Existing conversation: load ALL messages (no product filter). Otherwise use product_id from URL.
            const productId = conv ? null : productIdToSelect;
            
            // Use conversation user name if found, otherwise use default
            const userName = conv ? (conv.other_user_name || 'ユーザー') : 'ユーザー';
            
            // Auto-select after delay - longer on mobile for layout to settle
            const isMobile = window.innerWidth <= 768;
            const delay = isMobile ? 450 : 300;
            setTimeout(() => {
                const isFromProductDetail = !!(userIdToSelect && (productIdToSelect || messageType === 'product'));
                window._wasExistingConversation = !!conv; // conv ada = pernah chat sebelumnya
                selectConversation(userId, userName, productId, isFromProductDetail, true); // true = autoSelect
                
                // Clear pending params after selection
                window.pendingConversationUserId = null;
                window.pendingConversationProductId = null;
            }, delay);
        }
    } catch (error) {
        console.error('Error loading conversations:', error);
        const container = document.getElementById('conversationsList');
        if (container) {
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">会話の読み込みに失敗しました</p>
                    <button onclick="loadConversations()" class="btn btn-primary mt-3">
                        <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                    </button>
                </div>
            `;
        }
    }
}

function displayConversations(conversations) {
    const container = document.getElementById('conversationsList');
    if (!container) return;
    
    if (conversations && Array.isArray(conversations) && conversations.length > 0) {
        const conversationsHtml = conversations.map((conv, index) => {
            try {
                const unreadCount = conv.unread_count || 0;
                const lastMessage = conv.last_message || 'メッセージなし';
                const userName = escapeHtml(conv.other_user_name || 'ユーザー');
                
                const avatarUrl = conv.other_user_avatar_url;
                const resolvedAvatarUrl = KaruruUtils.resolveAvatarUrl(avatarUrl);
                const defaultAvatar = KaruruUtils.resolveAvatarUrl('');
                let avatarHtml;
                if (avatarUrl && typeof avatarUrl === 'string' && avatarUrl.trim() !== '' && avatarUrl !== 'null') {
                    avatarHtml = `<img src="${resolvedAvatarUrl}" alt="${userName}" class="conversation-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">`;
                } else {
                    avatarHtml = `<img src="${defaultAvatar}" alt="${userName}" class="conversation-avatar-img" onerror="this.onerror=null; this.style.display='none'; this.parentElement.innerHTML='<i class=\\'bi bi-person-circle-fill\\'></i>';"">`;
                }
                
                const otherUserId = conv.other_user_id || 0;
                const escapedUserName = userName.replace(/'/g, "\\'").replace(/"/g, '&quot;');
                return `
                    <div class="conversation-item" data-user-id="${otherUserId}"
                         onclick="selectConversation(${otherUserId}, '${escapedUserName}', null, false)">
                        <div class="conversation-avatar">
                            ${avatarHtml}
                            ${unreadCount > 0 ? `<span class="unread-dot"></span>` : ''}
                        </div>
                        <div class="conversation-content">
                            <div class="conversation-header">
                                <h6 class="conversation-name mb-0">${userName}</h6>
                                ${conv.last_message_time ? `
                                    <span class="conversation-time">${formatConversationListDate(conv.last_message_time)}</span>
                                ` : ''}
                            </div>
                            <div class="conversation-preview">
                                <p class="mb-0">${escapeHtml(lastMessage)}</p>
                                ${unreadCount > 0 ? `
                                    <span class="unread-badge">${unreadCount}</span>
                                ` : ''}
                            </div>
                        </div>
                    </div>
                `;
            } catch (e) {
                console.error(`Error rendering conversation ${index}:`, e, conv);
                return '';
            }
        }).filter(html => html && html !== '').join('');
        
        container.innerHTML = conversationsHtml;
    } else {
        container.innerHTML = `
            <div class="text-center p-5 text-muted">
                <i class="bi bi-inbox" style="font-size: 4rem; opacity: 0.5;"></i>
                <p class="mt-3 mb-0">会話がありません</p>
            </div>
        `;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function formatMessageTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
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
}

function formatMessageTimeOnly(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit' });
}

function formatMessageDate(date) {
    if (!date) return '';
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) {
        return '今日';
    } else if (date.toDateString() === yesterday.toDateString()) {
        return '昨日';
    } else {
        return date.toLocaleDateString('ja-JP', { month: 'long', day: 'numeric' });
    }
}

function formatConversationListDate(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) {
        return '今日';
    } else if (date.toDateString() === yesterday.toDateString()) {
        return '昨日';
    } else {
        return date.toLocaleDateString('ja-JP', { month: 'long', day: 'numeric' });
    }
}

function selectConversation(userId, userName, productId = null, fromProductDetail = false, isAutoSelect = false) {
    window._openedFromProductDetail = fromProductDetail;
    currentConversationId = userId;
    
    // Store product ID - existing conversation: no product filter (load all messages)
    if (window._skipAutoSelect) {
        currentProductId = null; // Reset: hapus konteks product
    } else if (productId) {
        currentProductId = productId;
    } else if (window._wasExistingConversation) {
        currentProductId = null; // Existing conv: jangan filter by product
    } else {
        const urlParams = new URLSearchParams(window.location.search);
        const urlProductId = urlParams.get('product_id');
        if (urlProductId) {
            currentProductId = parseInt(urlProductId);
        } else {
            currentProductId = null;
        }
    }
    
    const titleEl = document.getElementById('conversationTitle');
    if (titleEl) {
        const conv = currentConversationsList.find(c => {
            const otherId = c.other_user_id ?? c.user_id;
            return otherId != null && String(otherId) === String(userId);
        });
        const avatarUrl = conv?.other_user_avatar_url;
        const resolvedAvatarUrl = avatarUrl && typeof avatarUrl === 'string' && avatarUrl.trim() !== '' && avatarUrl !== 'null'
            ? KaruruUtils.resolveAvatarUrl(avatarUrl) : KaruruUtils.resolveAvatarUrl('');
        const defaultAvatar = KaruruUtils.resolveAvatarUrl('');
        window._currentConversationAvatarUrl = resolvedAvatarUrl;
        window._currentConversationUserName = userName;
        const isMobile = window.innerWidth <= 768;
        if (isMobile) {
            titleEl.innerHTML = `
                <button class="btn btn-sm btn-outline-dark messages-back-btn me-2" onclick="closeConversation()" title="戻る">
                    <i class="bi bi-arrow-left"></i>
                </button>
                <div class="conversation-header-avatar me-2">
                    <img src="${resolvedAvatarUrl}" alt="${escapeHtml(userName)}" class="conversation-header-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">
                </div>
                <span class="conversation-title-text">${escapeHtml(userName)}</span>
            `;
        } else {
            titleEl.innerHTML = `
                <div class="conversation-header-avatar me-2 d-inline-flex">
                    <img src="${resolvedAvatarUrl}" alt="${escapeHtml(userName)}" class="conversation-header-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">
                </div>
                <span>${escapeHtml(userName)}</span>
            `;
        }
    }
    const noConversationEl = document.getElementById('noConversation');
    const conversationViewEl = document.getElementById('conversationView');
    
    if (noConversationEl) {
        noConversationEl.classList.add('d-none');
    }
    
    if (conversationViewEl) {
        conversationViewEl.classList.remove('d-none');
        conversationViewEl.style.removeProperty('display');
    }
    
    // Hide sidebar on mobile when conversation is selected
    if (window.innerWidth <= 768) {
        const sidebar = document.querySelector('.messages-sidebar');
        if (sidebar) {
            sidebar.style.display = 'none';
        }
        document.querySelector('.messages-row')?.classList.add('conversation-open-mobile');
    }
    
    // Load messages - on mobile auto-select, defer until layout has painted
    const doLoadMessages = () => loadMessages(userId, currentProductId);
    if (isAutoSelect && window.innerWidth <= 768) {
        requestAnimationFrame(() => {
            requestAnimationFrame(doLoadMessages);
        });
    } else {
        doLoadMessages();
    }
    
    // Mark conversation as active
    document.querySelectorAll('.conversation-item').forEach(item => {
        item.classList.remove('active');
    });
    const activeItem = event?.currentTarget?.closest('.conversation-item') 
        || document.querySelector(`.conversation-item[data-user-id="${userId}"]`);
    if (activeItem) activeItem.classList.add('active');
}

// Helper function to get current conversations
function getCurrentConversations() {
    return currentConversationsList;
}

function filterConversations() {
    const searchInput = document.getElementById('conversationSearchInput');
    const searchTerm = searchInput ? searchInput.value.toLowerCase().trim() : '';
    
    if (!searchTerm) {
        // Show all conversations if search is empty
        displayConversations(currentConversationsList);
        return;
    }
    
    // Filter conversations by user name or last message
    const filtered = currentConversationsList.filter(conv => {
        const userName = (conv.other_user_name || '').toLowerCase();
        const lastMessage = (conv.last_message || '').toLowerCase();
        return userName.includes(searchTerm) || lastMessage.includes(searchTerm);
    });
    
    displayConversations(filtered);
}

function closeConversation() {
    try {
        // Clear typing indicator timeout before clearing conversation (prevents error when timeout fires)
        if (typingTimeout) {
            clearTimeout(typingTimeout);
            typingTimeout = null;
        }
        if (isTyping && window.webSocketManager && currentConversationId) {
            try {
                window.webSocketManager.sendTypingIndicator(currentConversationId, false);
            } catch (e) { /* ignore */ }
            isTyping = false;
        }
        
        const wasFromProductDetail = window._openedFromProductDetail === true;
        const wasExistingConversation = window._wasExistingConversation === true;
        const closedUserId = currentConversationId;
        currentConversationId = null;
        currentProductId = null;
        window._openedFromProductDetail = false;
        window._wasExistingConversation = false;
        
        // Jika dari product detail (ask seller): redirect ke mode default (daftar chat)
        if (wasFromProductDetail) {
            const contextPath = window.CONTEXT_PATH || '';
            window.location.href = contextPath + '/messages.jsp?reset=1';
            return;
        }
        
        const noConversationEl = document.getElementById('noConversation');
        const conversationViewEl = document.getElementById('conversationView');
        if (noConversationEl) {
            noConversationEl.classList.remove('d-none');
            noConversationEl.style.removeProperty('display');
        }
        if (conversationViewEl) {
            conversationViewEl.classList.add('d-none');
            conversationViewEl.style.setProperty('display', 'none', 'important');
        }
    } catch (e) {
        console.error('Error in closeConversation:', e);
    }
    
    // Show sidebar on mobile when conversation is closed
    if (window.innerWidth <= 768) {
        const sidebar = document.querySelector('.messages-sidebar');
        if (sidebar) {
            sidebar.style.display = 'block';
        }
        document.querySelector('.messages-row')?.classList.remove('conversation-open-mobile');
    }
    
    // Clear message list and remove typing indicator
    const messagesList = document.getElementById('messagesList');
    if (messagesList) {
        const typingIndicator = document.getElementById('typingIndicator');
        if (typingIndicator) typingIndicator.remove();
        messagesList.innerHTML = '';
    }
    
    const conversationItems = document.querySelectorAll('.conversation-item');
    if (conversationItems) {
        conversationItems.forEach(item => item.classList.remove('active'));
    }
}

async function showOrderInfo(productId, isOrder = false) {
    // Get order information from product ID and redirect accordingly
    if (!productId) return;
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        
        if (isOrder) {
            // If it's an order context, try to get order information
            const orderId = window.pendingOrderId;
            if (orderId) {
                window.location.href = `${contextPath}/orderconfirm.jsp?id=${orderId}`;
                return;
            }
            
            // Try to find order by product ID
            const response = await fetch(`${contextPath}/OrderServlet?action=getOrderByProduct&product_id=${productId}`);
            const data = await response.json();
            
            if (data.success && data.order_id) {
                // Redirect to order detail page
                window.location.href = `${contextPath}/orderconfirm.jsp?id=${data.order_id}`;
                return;
            }
        }
        
        // Fallback to product detail page
        window.location.href = `${contextPath}/product-detail.jsp?id=${productId}`;
    } catch (error) {
        console.error('Error getting order info:', error);
        // Fallback to product detail page
        const contextPath = window.CONTEXT_PATH || '';
        window.location.href = `${contextPath}/product-detail.jsp?id=${productId}`;
    }
}

async function loadMessages(userId, productId = null) {
    try {
        if (!userId) {
            console.error('Invalid userId:', userId);
            return;
        }
        
        const container = document.getElementById('messagesList');
        if (!container) {
            console.error('Messages container not found');
            return;
        }
        
        // Show loading state
        container.innerHTML = `
            <div class="text-center p-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
                <p class="mt-2 text-muted">メッセージを読み込み中...</p>
            </div>
        `;
        
        // Jangan pakai product_id jika: reset, atau existing conversation (load all messages)
        if (window._skipAutoSelect || window._wasExistingConversation) {
            productId = null;
            currentProductId = null;
        } else if (!productId && currentProductId) {
            productId = currentProductId;
        } else if (!productId) {
            const urlParams = new URLSearchParams(window.location.search);
            const urlProductId = urlParams.get('product_id');
            if (urlProductId) {
                productId = parseInt(urlProductId);
                currentProductId = productId;
            }
        }
        
        const contextPath = window.CONTEXT_PATH || '';
        let url = `${contextPath}/MessagesServlet?action=getMessages&other_user_id=${userId}`;
        if (productId) {
            url += `&product_id=${productId}`;
            // Store product ID for this conversation
            currentProductId = productId;
        }
        
        const response = await fetch(url);
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Failed to load messages:', response.status, errorText);
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">メッセージの読み込みに失敗しました (${response.status})</p>
                    <button class="btn btn-primary mt-3" onclick="loadMessages(${userId}, ${productId || 'null'})">
                        <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                    </button>
                </div>
            `;
            return;
        }
        
        const contentType = response.headers.get('content-type');
        let data = {};
        
        if (contentType && contentType.includes('application/json')) {
            data = await response.json();
        } else {
            const text = await response.text();
            try {
                data = JSON.parse(text);
            } catch (e) {
                console.error('Failed to parse messages response:', e);
                return;
            }
        }
        
        const messages = data.messages || data.data || (Array.isArray(data) ? data : []);
        const currentUserId = window.currentUserId || parseInt(sessionStorage.getItem('userId') || '0');
        
        if (messages && Array.isArray(messages) && messages.length > 0) {
            let lastDate = null;
            let productInfoShown = false; // Track if product info has been shown already
            
            const messagesHtml = messages.map((msg, index) => {
                try {
                    const isSent = msg.sender_id === currentUserId;
                    const messageTime = msg.sent_at || msg.created_at;
                    const messageDate = messageTime ? new Date(messageTime) : null;
                    const showDateSeparator = lastDate === null || 
                        (messageDate && (messageDate.getDate() !== lastDate.getDate() || 
                         messageDate.getMonth() !== lastDate.getMonth() || 
                         messageDate.getFullYear() !== lastDate.getFullYear()));
                    
                    if (messageDate) {
                        lastDate = messageDate;
                    }
                    
                    const formattedTime = messageTime ? formatMessageTimeOnly(messageTime) : '';
                    const formattedDate = messageDate ? formatMessageDate(messageDate) : '';
                    
                    // Get avatar URL
                    // For received messages (!isSent), show sender's avatar (the person who sent the message to you)
                    // For sent messages (isSent), we don't show avatar (message is on the right side, aligned to right)
                    let avatarUrl, avatarHtml = '';
                    
                    if (!isSent) {
                        // Message received from other user - show sender's avatar (the person who sent it)
                        // When !isSent: sender_id = other user (who sent the message), receiver_id = current user (you)
                        // So we use sender_avatar_url to show the avatar of the person who sent the message
                        avatarUrl = msg.sender_avatar_url;
                        
                        const resolvedAvatarUrl = avatarUrl && typeof avatarUrl === 'string' && avatarUrl.trim() !== '' && avatarUrl !== 'null' 
                            ? KaruruUtils.resolveAvatarUrl(avatarUrl) 
                            : KaruruUtils.resolveAvatarUrl('');
                        const defaultAvatar = KaruruUtils.resolveAvatarUrl('');
                        
                        avatarHtml = `<img src="${resolvedAvatarUrl}" alt="" class="message-avatar-img" onerror="this.onerror=null; this.src='${defaultAvatar}'; this.onerror=null;">`;
                    }
                    // For sent messages (isSent), no avatar is shown (message appears on the right side)
                    
                    // Attachment handling
                    let attachmentHtml = '';
                    if (msg.attachment_url && typeof msg.attachment_url === 'string' && msg.attachment_url.trim() !== '' && msg.attachment_url !== 'null') {
                        try {
                            const attachmentPath = msg.attachment_url.startsWith('http') ? 
                                msg.attachment_url : 
                                `${contextPath}/${msg.attachment_url}`;
                            const isImage = /\.(jpg|jpeg|png|gif|webp|bmp|svg)$/i.test(msg.attachment_url);
                            if (isImage) {
                                attachmentHtml = `
                                    <div class="message-attachment mb-2">
                                        <img src="${attachmentPath}" alt="Attachment" class="message-attachment-image" onclick="window.open('${attachmentPath}', '_blank')" onerror="this.style.display='none';">
                                    </div>
                                `;
                            } else {
                                attachmentHtml = `
                                    <div class="message-attachment mb-2">
                                        <a href="${attachmentPath}" target="_blank" class="btn btn-sm btn-outline-primary">
                                            <i class="bi bi-paperclip me-1"></i>添付ファイルを開く
                                        </a>
                                    </div>
                                `;
                            }
                        } catch (e) {
                            console.error('Error processing attachment:', e);
                        }
                    }
                    
                    // Order info template (if product_id exists) - Only show in first message with product_id
                    let orderInfoHtml = '';
                    if (msg.product_id && !productInfoShown) {
                        try {
                            const productName = escapeHtml(msg.product_name || '商品');
                            let productImage;
                            if (msg.product_image_url && typeof msg.product_image_url === 'string' && msg.product_image_url.trim() !== '' && msg.product_image_url !== 'null') {
                                productImage = msg.product_image_url.startsWith('http') ? 
                                    msg.product_image_url : 
                                    `${contextPath}/${msg.product_image_url}`;
                            } else {
                                productImage = `${contextPath}/img/default-product.png`;
                            }
                            const productId = parseInt(msg.product_id) || 0;
                            if (productId > 0) {
                                // Determine label based on message type or context
                                // Check if this conversation is for order (from URL parameter) or product inquiry
                                // Also check currentProductId for context
                                const messageType = window.pendingMessageType || 'product'; // Get from URL params or default to product
                                const isOrderContext = (messageType === 'order') || 
                                                      (window.pendingOrderId !== null && window.pendingOrderId !== undefined) ||
                                                      (msg.message_text && (msg.message_text.includes('注文') || msg.message_text.includes('order')));
                                const infoLabel = isOrderContext ? '注文情報' : '商品情報';
                                const infoIcon = isOrderContext ? 'bi-receipt' : 'bi-box-seam';
                                const buttonText = isOrderContext ? '注文を確認' : '詳細を見る';
                                
                                orderInfoHtml = `
                                    <div class="message-order-info" onclick="showOrderInfo(${productId}, ${isOrderContext ? 'true' : 'false'})" style="cursor: pointer;">
                                        <div class="order-product-image-wrapper">
                                            <img src="${productImage}" alt="${productName}" class="order-product-image" onerror="this.onerror=null; this.src='${KaruruUtils.resolveProductImageUrl('')}';">
                                        </div>
                                        <div class="order-info-content">
                                            <div class="order-info-label">
                                                <i class="bi ${infoIcon}"></i>${infoLabel}
                                            </div>
                                            <div class="order-info-name">${escapeHtml(productName)}</div>
                                            <button class="btn btn-sm btn-outline-primary order-info-btn" onclick="event.stopPropagation(); showOrderInfo(${productId}, ${isOrderContext ? 'true' : 'false'})">
                                                <i class="bi bi-info-circle"></i>${buttonText}
                                            </button>
                                        </div>
                                    </div>
                                `;
                                // Mark that product info has been shown
                                productInfoShown = true;
                            }
                        } catch (e) {
                            console.error('Error processing order info:', e);
                        }
                    }
                    
                    return `
                        ${showDateSeparator ? `
                            <div class="message-date-separator">
                                <span>${formattedDate}</span>
                            </div>
                        ` : ''}
                        <div class="message-wrapper ${isSent ? 'message-sent-wrapper' : 'message-received-wrapper'}" data-message-id="${msg.message_id || ''}" data-is-sent="${isSent}">
                            ${!isSent && avatarHtml ? `<div class="message-avatar-container">${avatarHtml}</div>` : ''}
                            <div class="message-bubble ${isSent ? 'message-sent' : 'message-received'} ${attachmentHtml ? 'has-attachment' : ''}" data-message-id="${msg.message_id || ''}">
                                ${orderInfoHtml}
                                ${attachmentHtml}
                                ${msg.message_text && msg.message_text.trim() !== '' ? `
                                    <div class="message-text">${escapeHtml(msg.message_text)}</div>
                                ` : ''}
                                <div class="message-footer">
                                    <span class="message-time">${formattedTime}</span>
                                    ${isSent ? `
                                        <span class="read-indicator">
                                            ${msg.is_read ? '<i class="bi bi-check2-all text-primary"></i>' : '<i class="bi bi-check2 text-muted"></i>'}
                                        </span>
                                    ` : ''}
                                </div>
                            </div>
                        </div>
                    `;
                } catch (e) {
                    console.error(`Error rendering message ${index}:`, e, msg);
                    return '';
                }
            }).filter(html => html && html !== '').join('');
            
            // Set container innerHTML with messages
            container.innerHTML = messagesHtml;
            
            // Scroll to bottom with smooth animation
            setTimeout(() => {
                if (container) {
                    container.scrollTo({
                        top: container.scrollHeight,
                        behavior: 'smooth'
                    });
                }
            }, 150);
            
            // Mark messages as read
            markMessagesAsRead(userId);
            
            // Update conversation list to reflect read status
            loadConversations();
        } else {
            container.innerHTML = `
                <div class="text-center p-5 text-muted">
                    <i class="bi bi-chat-left-text" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">メッセージがありません</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading messages:', error);
        const container = document.getElementById('messagesList');
        if (container) {
            container.innerHTML = `
                <div class="text-center p-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">メッセージの読み込みに失敗しました</p>
                    <small class="text-muted">${error.message || ''}</small>
                </div>
            `;
        }
    }
}

async function markMessagesAsRead(otherUserId) {
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const body = {
            action: 'markAsRead',
            other_user_id: otherUserId
        };
        if (currentProductId) {
            body.product_id = currentProductId;
        }
        await fetch(`${contextPath}/MessagesServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        });
    } catch (error) {
        console.error('Error marking messages as read:', error);
    }
}

function handleTyping() {
    if (!currentConversationId) return;
    
    // Stop typing indicator after 3 seconds of no typing
    if (typingTimeout) {
        clearTimeout(typingTimeout);
    }
    
    if (!isTyping) {
        isTyping = true;
        if (window.webSocketManager) {
            window.webSocketManager.sendTypingIndicator(currentConversationId, true);
        }
    }
    
    typingTimeout = setTimeout(() => {
        isTyping = false;
        if (window.webSocketManager && currentConversationId) {
            try {
                window.webSocketManager.sendTypingIndicator(currentConversationId, false);
            } catch (e) { /* ignore */ }
        }
    }, 3000);
}

function setupWebSocketHandlers() {
    // For polling fallback - refresh messages when new data detected
    window.refreshMessages = function() {
        if (typeof loadConversations === 'function') {
            loadConversations();
        }
        if (currentConversationId && typeof loadMessages === 'function') {
            loadMessages(currentConversationId, currentProductId);
        }
    };
    
    // Update messages list when new message received via WebSocket (real-time)
    window.updateMessagesList = function(message) {
        if (!message) return;
        
        // Check if message is for current conversation
        const isForCurrentConversation = currentConversationId && 
            (parseInt(message.sender_id) === parseInt(currentConversationId) || 
             parseInt(message.receiver_id) === parseInt(currentConversationId));
        
        if (isForCurrentConversation) {
            // Reload messages for current conversation
            loadMessages(currentConversationId, currentProductId);
        }
        
        // Always update conversation list to show new messages
        loadConversations();
    };
    
    // Show typing indicator
    window.showTypingIndicator = function(userId, userName) {
        if (currentConversationId === userId) {
            const container = document.getElementById('messagesList');
            if (container) {
                const typingDiv = document.createElement('div');
                typingDiv.className = 'typing-indicator';
                typingDiv.id = 'typingIndicator';
                typingDiv.innerHTML = `<span>${userName}が入力中...</span>`;
                container.appendChild(typingDiv);
                container.scrollTop = container.scrollHeight;
                
                setTimeout(() => {
                    const indicator = document.getElementById('typingIndicator');
                    if (indicator) indicator.remove();
                }, 3000);
            }
        }
    };
    
    // Update message read status (single message)
    window.updateMessageReadStatus = function(messageId) {
        const messageElement = document.querySelector(`[data-message-id="${messageId}"]`);
        if (messageElement) {
            messageElement.classList.add('read');
        }
    };
    
    // Update all sent messages as read when receiver views chat (real-time 既読)
    window.updateMessagesReadStatus = function(receiverId, productId) {
        const rid = parseInt(receiverId);
        const pid = productId ? parseInt(productId) : 0;
        if (currentConversationId !== rid) return;
        if (pid !== 0 && currentProductId && pid !== currentProductId) return;
        const container = document.getElementById('messagesList');
        if (!container) return;
        container.querySelectorAll('.message-sent-wrapper .read-indicator').forEach(function(indicator) {
            indicator.innerHTML = '<i class="bi bi-check2-all text-primary"></i>';
        });
    };
    
    // Update unread badge
    window.updateUnreadBadge = function(count) {
        const badge = document.querySelector('.unread-badge');
        if (badge) {
            if (count > 0) {
                badge.textContent = count;
                badge.style.display = 'inline-block';
            } else {
                badge.style.display = 'none';
            }
        }
    };
}

let selectedAttachments = [];

function handleAttachmentSelect(e) {
    const files = e.target.files;
    if (!files || files.length === 0) return;
    
    // Handle image attachments
    Array.from(files).forEach(file => {
        if (file.type.startsWith('image/')) {
            // Validate file size (max 5MB)
            if (file.size > 5 * 1024 * 1024) {
                return;
            }
            
            const reader = new FileReader();
            reader.onload = function(event) {
                selectedAttachments.push({
                    file: file,
                    dataUrl: event.target.result,
                    name: file.name
                });
                
                // Show preview
                showAttachmentPreview();
            };
            reader.readAsDataURL(file);
        }
    });
    
    // Reset input
    e.target.value = '';
}

function showAttachmentPreview() {
    // Remove existing preview
    const existingPreview = document.getElementById('attachmentPreview');
    if (existingPreview) {
        existingPreview.remove();
    }
    
    if (selectedAttachments.length === 0) return;
    
    // Create preview container
    const previewContainer = document.createElement('div');
    previewContainer.id = 'attachmentPreview';
    previewContainer.className = 'attachment-preview mb-2';
    previewContainer.innerHTML = selectedAttachments.map((att, index) => `
        <div class="attachment-preview-item">
            <img src="${att.dataUrl}" alt="${att.name}" class="attachment-preview-img">
            <button type="button" class="btn btn-sm btn-outline-dark attachment-remove-btn" data-index="${index}">
                <i class="bi bi-x text-dark"></i>
            </button>
        </div>
    `).join('');
    
    // Insert before message form
    const messageForm = document.getElementById('messageForm');
    if (messageForm && messageForm.parentElement) {
        messageForm.parentElement.insertBefore(previewContainer, messageForm);
    }
    
    // Add remove button handlers
    previewContainer.querySelectorAll('.attachment-remove-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const index = parseInt(this.getAttribute('data-index'));
            selectedAttachments.splice(index, 1);
            showAttachmentPreview();
        });
    });
}

async function handleSendMessage(e) {
    e.preventDefault();
    
    if (!currentConversationId) {
        return;
    }
    
    const messageInput = document.getElementById('messageText');
    const messageText = messageInput.value.trim();
    
    if (!messageText && selectedAttachments.length === 0) {
        return;
    }
    
    // Disable input while sending
    if (messageInput) {
        messageInput.disabled = true;
    }
    
    // Stop typing indicator
    if (typingTimeout) {
        clearTimeout(typingTimeout);
        typingTimeout = null;
    }
    if (isTyping && window.webSocketManager) {
        window.webSocketManager.sendTypingIndicator(currentConversationId, false);
        isTyping = false;
    }
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        let response;
        
        // If there are attachments, send as multipart/form-data
        if (selectedAttachments.length > 0) {
            const formData = new FormData();
            formData.append('action', 'send');
            formData.append('receiver_id', currentConversationId.toString());
            if (currentProductId && !window._skipAutoSelect) {
                formData.append('product_id', currentProductId.toString());
            }
            if (messageText) {
                formData.append('message_text', messageText);
            }
            selectedAttachments.forEach((att, index) => {
                formData.append('attachment', att.file, att.name);
            });
            
            response = await fetch(`${contextPath}/MessagesServlet`, {
                method: 'POST',
                body: formData // No Content-Type header needed for FormData
            });
        } else {
            // Send as JSON if no attachments
            response = await fetch(`${contextPath}/MessagesServlet`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    action: 'send',
                    receiver_id: currentConversationId,
                    message_text: messageText,
                    ...(currentProductId && !window._skipAutoSelect && { product_id: currentProductId })
                })
            });
        }
        
        // Read response text first to avoid reading body twice
        const responseText = await response.text();
        
        if (!response.ok) {
            console.error('Server error response:', responseText);
            let errorMsg = `Failed to send message: ${response.status} ${response.statusText}`;
            try {
                const errorJson = JSON.parse(responseText);
                errorMsg = errorJson.error || errorJson.message || errorMsg;
            } catch (e) {
                // Use default error message
            }
            throw new Error(errorMsg);
        }
        
        // Parse response
        let result = {};
        try {
            result = JSON.parse(responseText);
        } catch (e) {
            console.warn('Response is not JSON, assuming success');
            result = { success: true };
        }
        
        if (result.success !== false) {
            // Clear input
            if (messageInput) {
                messageInput.value = '';
                messageInput.disabled = false;
                messageInput.focus();
            }
            
            // Clear attachments after successful send
            selectedAttachments = [];
            showAttachmentPreview();
            
            // Reload messages and conversations
            if (currentConversationId) {
                await loadMessages(currentConversationId, currentProductId);
            }
            loadConversations();
        } else {
            if (messageInput) {
                messageInput.disabled = false;
                messageInput.focus();
            }
        }
    } catch (error) {
        console.error('Error sending message:', error);
        const messageInput = document.getElementById('messageText');
        if (messageInput) {
            messageInput.disabled = false;
            messageInput.focus();
        }
    }
}

