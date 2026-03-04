<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>
<%
    if (currentUser == null) {
        response.sendRedirect("login.jsp?redirect=rentals");
        return;
    }
%>

<style>
.rentals-page {
    min-height: calc(100vh - 200px);
}

.rental-history-card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    box-shadow: var(--shadow);
    transition: all 0.3s ease;
    overflow: hidden;
    margin-bottom: 1.5rem;
}

.rental-history-card:hover {
    box-shadow: var(--shadow-hover);
    border-color: var(--primary-color);
    transform: translateY(-2px);
}

.rental-item-image {
    width: 100%;
    height: 150px;
    object-fit: cover;
    border-radius: 12px;
    background: var(--bg-secondary);
}

.rental-item-info {
    padding: 1.25rem;
}

.rental-item-header {
    display: flex;
    justify-content: space-between;
    align-items: start;
    margin-bottom: 1rem;
}

.rental-item-title {
    font-size: 1.1rem;
    font-weight: 600;
    color: var(--text-color);
    margin: 0;
}

.rental-item-meta {
    color: var(--text-muted);
    font-size: 0.9rem;
    margin-bottom: 0.5rem;
}

.rental-item-price {
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--bs-warning);
}

.rental-status-badge {
    font-size: 0.85rem;
    padding: 0.4rem 0.75rem;
    border-radius: 6px;
    font-weight: 600;
}

.rental-empty-state {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    padding: 4rem 2rem;
    text-align: center;
}

@media (max-width: 768px) {
    .rental-item-image {
        height: 120px;
    }
    
    .rental-item-info {
        padding: 1rem;
    }
    
    .rentals-filter-row {
        flex-direction: column;
        align-items: stretch !important;
    }
    
    .rentals-filter-row .form-select {
        max-width: 100% !important;
    }
}
</style>

<main class="page-main py-4 rentals-page">
    <div class="container">
        <div class="row mb-4">
            <div class="col-12">
                <h1 class="fw-bold mb-2 page-header">
                    <i class="bi bi-calendar-check me-2 text-warning"></i>レンタル履歴
                </h1>
                <p class="text-muted mb-0">あなたのレンタル予約と履歴を確認できます</p>
            </div>
        </div>
        
        <div class="card shadow-lg border-0 mb-4">
            <div class="card-header card-header-light border-bottom">
                <div class="d-flex justify-content-between align-items-center flex-wrap gap-3 rentals-filter-row">
                    <div class="d-flex align-items-center gap-3 flex-wrap">
                        <h5 class="mb-0 fw-bold">
                            <i class="bi bi-funnel me-2"></i>フィルター
                        </h5>
                        <div class="btn-group" role="group">
                            <input type="radio" class="btn-check" name="rentalRole" id="roleRenter" value="renter" checked>
                            <label class="btn btn-outline-primary btn-sm" for="roleRenter">借りたレンタル</label>
                            <input type="radio" class="btn-check" name="rentalRole" id="roleOwner" value="owner">
                            <label class="btn btn-outline-primary btn-sm" for="roleOwner">貸したレンタル</label>
                        </div>
                    </div>
                    <select class="form-select form-control-light" id="statusFilter" style="max-width: 250px;">
                        <option value="">すべてのレンタル</option>
                        <option value="pending">保留中</option>
                        <option value="confirmed">確定済み</option>
                        <option value="active">利用中</option>
                        <option value="completed">完了</option>
                        <option value="cancelled">キャンセル</option>
                    </select>
                </div>
            </div>
            <div class="card-body">
                <div id="rentalsList">
                    <div class="text-center py-5">
                        <div class="spinner-border text-primary" role="status" style="width: 3rem; height: 3rem;">
                            <span class="visually-hidden">読み込み中...</span>
                        </div>
                        <p class="text-muted mt-3">レンタル履歴を読み込んでいます...</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<!-- Payment Modal -->
<div class="modal fade" id="paymentRentalModal" tabindex="-1" aria-labelledby="paymentRentalModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="paymentRentalModalLabel">
                    <i class="bi bi-credit-card me-2"></i>レンタル料金の支払い
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p class="mb-3">支払い方法を選択してください</p>
                <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" name="rentalPaymentMethod" id="pmWallet" value="wallet" checked>
                    <label class="form-check-label" for="pmWallet">
                        <i class="bi bi-wallet2 me-1"></i>ウォレット
                    </label>
                </div>
                <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" name="rentalPaymentMethod" id="pmCredit" value="credit_card">
                    <label class="form-check-label" for="pmCredit">
                        <i class="bi bi-credit-card me-1"></i>クレジットカード
                    </label>
                </div>
                <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" name="rentalPaymentMethod" id="pmBank" value="bank_transfer">
                    <label class="form-check-label" for="pmBank">
                        <i class="bi bi-bank me-1"></i>銀行振込
                    </label>
                </div>
                <div class="form-check mb-2">
                    <input class="form-check-input" type="radio" name="rentalPaymentMethod" id="pmCod" value="cod">
                    <label class="form-check-label" for="pmCod">
                        <i class="bi bi-cash-stack me-1"></i>代金引換
                    </label>
                </div>
                <div class="mt-3 p-2 bg-light rounded">
                    <strong>支払い金額:</strong> <span id="paymentRentalAmount" class="text-warning fw-bold">¥0</span>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                <button type="button" class="btn btn-success" id="confirmPaymentBtn">
                    <i class="bi bi-check-circle me-1"></i>支払う
                </button>
            </div>
        </div>
    </div>
</div>

<!-- Complete Rental Modal -->
<div class="modal fade" id="completeRentalModal" tabindex="-1" aria-labelledby="completeRentalModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="completeRentalModalLabel">
                    <i class="bi bi-check2-circle me-2"></i>返却完了
                </h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p>レンタル商品を返却しましたか？</p>
                <p class="text-muted small">返却完了を押すと、在庫が自動的に復元されます。</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                <button type="button" class="btn btn-success" id="confirmCompleteBtn">
                    <i class="bi bi-check-circle me-1"></i>返却完了
                </button>
            </div>
        </div>
    </div>
</div>

<!-- Cancel Rental Modal -->
<div class="modal fade" id="cancelRentalModal" tabindex="-1" aria-labelledby="cancelRentalModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title" id="cancelRentalModalLabel">レンタルをキャンセル</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p>このレンタルをキャンセルしてもよろしいですか？</p>
                <p class="text-muted small">キャンセル後、在庫は自動的に復元されます。</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">閉じる</button>
                <button type="button" class="btn btn-danger" id="confirmCancelBtn">キャンセルする</button>
            </div>
        </div>
    </div>
</div>

<script src="${pageContext.request.contextPath}/js/rentals.js"></script>
<%@ include file="includes/footer.jsp" %>

