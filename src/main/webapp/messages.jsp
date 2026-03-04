<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=messages");
        return;
    }
%>

<main class="messages-page py-4">
    <div class="container-fluid px-4">
        <div class="d-flex justify-content-between align-items-center mb-4">
            <h1 class="h2 mb-0 fw-bold page-header">
                <i class="bi bi-chat-dots-fill me-2 text-primary"></i>メッセージ
            </h1>
        </div>
        
        <div class="row g-4 messages-row" style="min-height: calc(100vh - 200px);">
            <!-- Conversations Sidebar -->
            <div class="col-lg-4 col-md-5 messages-sidebar-col">
                <div class="card shadow-lg border-0 h-100 messages-sidebar">
                    <div class="card-header card-header-light border-0 p-3">
                        <h5 class="mb-0 fw-bold d-flex align-items-center">
                            <i class="bi bi-chat-left-text-fill me-2"></i>会話一覧
                        </h5>
                        <!-- Search Input -->
                        <div class="mt-2">
                            <input type="text" 
                                   id="conversationSearchInput" 
                                   class="form-control form-control-sm form-control-light" 
                                   placeholder="会話を検索..." 
                                   onkeyup="filterConversations()">
                        </div>
                    </div>
                    <div class="card-body p-0" style="max-height: calc(100vh - 250px); overflow-x: hidden; overflow-y: auto;">
                        <div id="conversationsList" class="conversations-list">
                            <div class="text-center p-4">
                                <div class="spinner-border text-primary" role="status">
                                    <span class="visually-hidden">読み込み中...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Messages View -->
            <div class="col-lg-8 col-md-7 messages-view-col messages-view-col-pc">
                <!-- No Conversation Selected -->
                <div id="noConversation" class="card shadow-lg border-0 text-center messages-empty messages-empty-pc" style="min-height: calc(100vh - 200px);">
                    <div class="card-body d-flex flex-column justify-content-center align-items-center py-5">
                        <div class="empty-icon mb-4">
                            <i class="bi bi-chat-left-heart-fill"></i>
                        </div>
                        <h4 class="mt-4 mb-2 fw-bold page-header">会話を選択してください</h4>
                        <p class="text-muted mb-0">
                            <span class="d-none d-md-inline">左側のリストから会話を選択してメッセージを開始</span>
                            <span class="d-md-none">上のリストから会話を選択してメッセージを開始</span>
                        </p>
                    </div>
                </div>
                
                <!-- Conversation View -->
                <div id="conversationView" class="card shadow-lg border-0 messages-conversation d-none" style="min-height: calc(100vh - 200px);">
                    <div class="card-header card-header-light border-0 p-3">
                        <div class="d-flex justify-content-between align-items-center">
                            <h5 class="mb-0 fw-bold d-flex align-items-center" id="conversationTitle">
                                <span>会話相手</span>
                            </h5>
                            <button class="btn btn-sm btn-outline-light rounded-pill d-none d-md-block" onclick="closeConversation()" title="閉じる">
                                <i class="bi bi-x-lg"></i>
                            </button>
                        </div>
                    </div>
                    <div class="card-body p-4 messages-container messages-container-light" id="messagesList" style="overflow-y: auto; overflow-x: hidden;">
                        <div class="text-center p-4">
                            <div class="spinner-border text-primary" role="status">
                                <span class="visually-hidden">読み込み中...</span>
                            </div>
                        </div>
                    </div>
                    <div class="card-footer card-header-light border-top p-3">
                        <form id="messageForm" class="message-form" enctype="multipart/form-data">
                            <input type="file" id="attachmentInput" accept="image/*" style="display: none;" multiple>
                            <div class="input-group input-group-lg message-input-group">
                                <button type="button" class="btn btn-outline-secondary rounded-start attachment-btn" title="添付ファイル" style="border-right: none;">
                                    <i class="bi bi-paperclip"></i>
                                </button>
                                <input type="text" 
                                       class="form-control form-control-light message-input" 
                                       id="messageText" 
                                       placeholder="メッセージを入力..." 
                                       autocomplete="off"
                                       style="border-left: none; border-right: none;">
                                <button type="button" class="btn btn-outline-secondary emoji-btn" title="絵文字・スタンプ" style="border-left: none; border-right: none;">
                                    <i class="bi bi-emoji-smile"></i>
                                </button>
                                <button type="submit" class="btn btn-primary rounded-end message-send-btn" title="送信">
                                    <i class="bi bi-send-fill"></i>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
    <!-- Emoji/Sticker Picker Modal - outside card for correct positioning & close -->
    <div class="modal fade" id="emojiPickerModal" tabindex="-1" aria-labelledby="emojiPickerModalLabel" aria-hidden="true" data-bs-backdrop="true" data-bs-keyboard="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title" id="emojiPickerModalLabel">絵文字・スタンプ</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">
                    <div class="emoji-picker-tabs mb-3">
                        <ul class="nav nav-tabs" role="tablist">
                            <li class="nav-item" role="presentation">
                                <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#emojiTab" type="button">😀 絵文字</button>
                            </li>
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" data-bs-toggle="tab" data-bs-target="#stickerTab" type="button">🎨 スタンプ</button>
                            </li>
                        </ul>
                    </div>
                    <div class="tab-content">
                        <div class="tab-pane fade show active" id="emojiTab" role="tabpanel">
                            <div class="emoji-grid">
                                <span class="emoji-item">😀</span><span class="emoji-item">😃</span><span class="emoji-item">😄</span><span class="emoji-item">😁</span>
                                <span class="emoji-item">😆</span><span class="emoji-item">😅</span><span class="emoji-item">🤣</span><span class="emoji-item">😂</span>
                                <span class="emoji-item">🙂</span><span class="emoji-item">🙃</span><span class="emoji-item">😉</span><span class="emoji-item">😊</span>
                                <span class="emoji-item">😇</span><span class="emoji-item">🥰</span><span class="emoji-item">😍</span><span class="emoji-item">🤩</span>
                                <span class="emoji-item">😘</span><span class="emoji-item">😗</span><span class="emoji-item">😚</span><span class="emoji-item">😙</span>
                                <span class="emoji-item">😋</span><span class="emoji-item">😛</span><span class="emoji-item">😜</span><span class="emoji-item">🤪</span>
                                <span class="emoji-item">😝</span><span class="emoji-item">🤑</span><span class="emoji-item">🤗</span><span class="emoji-item">🤭</span>
                                <span class="emoji-item">🤫</span><span class="emoji-item">🤔</span><span class="emoji-item">🤐</span><span class="emoji-item">🤨</span>
                                <span class="emoji-item">😐</span><span class="emoji-item">😑</span><span class="emoji-item">😶</span><span class="emoji-item">😏</span>
                                <span class="emoji-item">😒</span><span class="emoji-item">🙄</span><span class="emoji-item">😬</span><span class="emoji-item">🤥</span>
                                <span class="emoji-item">😌</span><span class="emoji-item">😔</span><span class="emoji-item">😪</span><span class="emoji-item">🤤</span>
                                <span class="emoji-item">😴</span><span class="emoji-item">😷</span><span class="emoji-item">🤒</span><span class="emoji-item">🤕</span>
                                <span class="emoji-item">🤢</span><span class="emoji-item">🤮</span><span class="emoji-item">🤧</span><span class="emoji-item">🥵</span>
                                <span class="emoji-item">🥶</span><span class="emoji-item">😵</span><span class="emoji-item">🤯</span><span class="emoji-item">🤠</span>
                                <span class="emoji-item">🥳</span><span class="emoji-item">😎</span><span class="emoji-item">🤓</span><span class="emoji-item">🧐</span>
                                <span class="emoji-item">👍</span><span class="emoji-item">👎</span><span class="emoji-item">👌</span><span class="emoji-item">✌️</span>
                                <span class="emoji-item">🤞</span><span class="emoji-item">🤟</span><span class="emoji-item">🤘</span><span class="emoji-item">🤙</span>
                                <span class="emoji-item">👏</span><span class="emoji-item">🙌</span><span class="emoji-item">👐</span><span class="emoji-item">🤲</span>
                                <span class="emoji-item">🙏</span><span class="emoji-item">✍️</span><span class="emoji-item">💪</span><span class="emoji-item">🦾</span>
                            </div>
                        </div>
                        <div class="tab-pane fade" id="stickerTab" role="tabpanel">
                            <div class="sticker-grid">
                                <div class="sticker-item">🎉</div>
                                <div class="sticker-item">🎊</div>
                                <div class="sticker-item">🎈</div>
                                <div class="sticker-item">🎁</div>
                                <div class="sticker-item">🎀</div>
                                <div class="sticker-item">🎂</div>
                                <div class="sticker-item">🍰</div>
                                <div class="sticker-item">🍕</div>
                                <div class="sticker-item">🍔</div>
                                <div class="sticker-item">🍟</div>
                                <div class="sticker-item">🌮</div>
                                <div class="sticker-item">🌯</div>
                                <div class="sticker-item">🍗</div>
                                <div class="sticker-item">🍖</div>
                                <div class="sticker-item">🍝</div>
                                <div class="sticker-item">🍜</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/messages.js"></script>
<%@ include file="includes/footer.jsp" %>
