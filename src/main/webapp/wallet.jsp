<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=wallet");
        return;
    }
%>

<main class="page-main py-4 wallet-page">
    <div class="container">
        <h1 class="fw-bold mb-4">
            <i class="bi bi-wallet2"></i> ウォレット
        </h1>
        
        <div class="row mb-4">
            <div class="col-md-8">
                <div class="card card-light">
                    <div class="card-header card-header-light d-flex justify-content-between align-items-center">
                        <h5 class="mb-0">残高</h5>
                        <button class="btn btn-sm btn-outline-light" id="refreshBalance" title="更新">
                            <i class="bi bi-arrow-clockwise"></i>
                        </button>
                    </div>
                    <div class="card-body text-center">
                        <h2 class="display-4 text-primary mb-4" id="walletBalance">
                            <span class="currency">¥</span>
                            <span class="amount">0</span>
                        </h2>
                        <div class="row">
                            <div class="col-4">
                                <div class="text-muted small">総収入</div>
                                <div class="fw-bold text-success" id="totalEarned">¥0</div>
                            </div>
                            <div class="col-4">
                                <div class="text-muted small">総支出</div>
                                <div class="fw-bold text-danger" id="totalSpent">¥0</div>
                            </div>
                            <div class="col-4">
                                <div class="text-muted small">凍結残高</div>
                                <div class="fw-bold" id="frozenBalance">¥0</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-md-4">
                <div class="card card-light">
                    <div class="card-body">
                        <h5 class="mb-3">クイックアクション</h5>
                        <div class="d-grid gap-2">
                            <button class="btn btn-primary" id="topupBtn" data-bs-toggle="modal" data-bs-target="#topupModal">
                                <i class="bi bi-plus-circle"></i> チャージ
                            </button>
                            <button class="btn btn-outline-primary" id="withdrawBtn" data-bs-toggle="modal" data-bs-target="#withdrawModal">
                                <i class="bi bi-dash-circle"></i> 出金
                            </button>
                            <button class="btn btn-outline-primary" id="transferBtn" data-bs-toggle="modal" data-bs-target="#transferModal">
                                <i class="bi bi-arrow-left-right"></i> 送金
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Transaction History -->
        <div class="card card-light">
            <div class="card-header card-header-light">
                <div class="d-flex justify-content-between align-items-center">
                    <h5 class="mb-0">取引履歴</h5>
                    <div class="btn-group wallet-filter-group" role="group">
                        <button type="button" class="btn btn-sm btn-outline-light active" data-filter="all">すべて</button>
                        <button type="button" class="btn btn-sm btn-outline-light" data-filter="deposit">入金</button>
                        <button type="button" class="btn btn-sm btn-outline-light" data-filter="withdrawal">出金</button>
                        <button type="button" class="btn btn-sm btn-outline-light" data-filter="purchase">購入</button>
                        <button type="button" class="btn btn-sm btn-outline-light" data-filter="earning">収入</button>
                    </div>
                </div>
            </div>
            <div class="card-body">
                <div id="transactionsList">
                    <!-- Transactions will be loaded here -->
                </div>
                <nav aria-label="Page navigation" class="mt-4">
                    <ul class="pagination justify-content-center" id="transactionsPagination">
                        <!-- Pagination will be loaded here -->
                    </ul>
                </nav>
            </div>
        </div>
    </div>
</main>

<!-- Top-up Modal -->
<div class="modal fade" id="topupModal" tabindex="-1" aria-labelledby="topupModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="topupModalLabel">ウォレットチャージ</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="topupForm">
                    <div class="mb-3">
                        <label for="topupAmount" class="form-label">チャージ金額</label>
                        <div class="input-group">
                            <span class="input-group-text input-group-text-light">¥</span>
                            <input type="number" class="form-control form-control-light" 
                                   id="topupAmount" name="amount" min="100" step="100" required placeholder="1000">
                        </div>
                        <small class="form-text text-muted">最低チャージ金額: ¥100</small>
                    </div>
                    <div class="mb-3">
                        <label>クイック選択</label>
                        <div class="d-grid gap-2 grid-auto-flow-column">
                            <button type="button" class="btn btn-outline-light btn-sm quick-amount-btn" data-amount="1000">¥1,000</button>
                            <button type="button" class="btn btn-outline-light btn-sm quick-amount-btn" data-amount="5000">¥5,000</button>
                            <button type="button" class="btn btn-outline-light btn-sm quick-amount-btn" data-amount="10000">¥10,000</button>
                            <button type="button" class="btn btn-outline-light btn-sm quick-amount-btn" data-amount="50000">¥50,000</button>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label for="paymentMethod" class="form-label">支払い方法</label>
                        <select class="form-select form-control-light" id="paymentMethod" name="payment_method" required>
                            <option value="">選択してください</option>
                            <option value="credit_card">クレジットカード</option>
                            <option value="bank_transfer">銀行振込</option>
                            <option value="convenience_store">コンビニ決済</option>
                            <option value="ewallet">電子マネー</option>
                        </select>
                    </div>
                    <div id="topupError" class="alert alert-danger d-none" role="alert"></div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="submit" class="btn btn-primary">チャージする</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>

<!-- Withdraw Modal -->
<div class="modal fade" id="withdrawModal" tabindex="-1" aria-labelledby="withdrawModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="withdrawModalLabel">出金申請</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="withdrawForm">
                    <div class="mb-3">
                        <label for="withdrawAmount" class="form-label">出金額</label>
                        <div class="input-group">
                            <span class="input-group-text input-group-text-light">¥</span>
                            <input type="number" class="form-control form-control-light" 
                                   id="withdrawAmount" name="amount" min="1000" step="1000" required placeholder="10000">
                        </div>
                        <small class="form-text text-muted">利用可能残高: <span id="availableBalance">¥0</span></small>
                    </div>
                    <div class="mb-3">
                        <label for="bankName" class="form-label">銀行名</label>
                        <input type="text" class="form-control form-control-light" 
                               id="bankName" name="bank_name" required placeholder="例: 三菱UFJ銀行">
                    </div>
                    <div class="mb-3">
                        <label for="bankAccount" class="form-label">口座番号</label>
                        <input type="text" class="form-control form-control-light" 
                               id="bankAccount" name="bank_account" required placeholder="例: 1234567">
                    </div>
                    <div class="mb-3">
                        <label for="accountHolder" class="form-label">口座名義</label>
                        <input type="text" class="form-control form-control-light" 
                               id="accountHolder" name="account_holder" required placeholder="カタカナで入力">
                    </div>
                    <div id="withdrawError" class="alert alert-danger d-none" role="alert"></div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="submit" class="btn btn-primary">出金申請</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>

<!-- Transfer Modal -->
<div class="modal fade" id="transferModal" tabindex="-1" aria-labelledby="transferModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="transferModalLabel">送金</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <form id="transferForm">
                    <div class="mb-3">
                        <label for="transferToUsername" class="form-label">受取人のユーザー名</label>
                        <div class="input-group">
                            <span class="input-group-text input-group-text-light">
                                <i class="bi bi-person"></i>
                            </span>
                            <input type="text" class="form-control form-control-light" 
                                   id="transferToUsername" name="to_username" required placeholder="ユーザー名を入力">
                        </div>
                        <small class="form-text text-muted">送金先のユーザー名を入力してください</small>
                        <div id="recipientInfo" class="mt-2 p-2 rounded" style="display: none; background: rgba(30, 136, 229, 0.1); border: 1px solid rgba(30, 136, 229, 0.3);">
                            <small class="text-primary" id="recipientName"></small>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label for="transferAmount" class="form-label">送金額</label>
                        <div class="input-group">
                            <span class="input-group-text input-group-text-light">¥</span>
                            <input type="number" class="form-control form-control-light" 
                                   id="transferAmount" name="amount" min="100" step="100" required placeholder="1000">
                        </div>
                        <small class="form-text text-muted">利用可能残高: <span id="availableBalanceForTransfer">¥0</span></small>
                    </div>
                    <div class="mb-3">
                        <label for="transferDescription" class="form-label">備考（任意）</label>
                        <textarea class="form-control form-control-light" 
                                  id="transferDescription" name="description" rows="3" 
                                  placeholder="送金の目的やメッセージを入力（任意）"></textarea>
                    </div>
                    <div id="transferError" class="alert alert-danger d-none" role="alert"></div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="submit" class="btn btn-primary">送金する</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/wallet.js"></script>
<%@ include file="includes/footer.jsp" %>
