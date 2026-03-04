// Wallet page JavaScript

let currentFilter = 'all';
let currentPage = 1;

document.addEventListener('DOMContentLoaded', function() {
    loadWalletBalance();
    loadTransactions();
    
    // Event listeners
    const refreshBtn = document.getElementById('refreshBalance');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadWalletBalance);
    }
    
    // Modal buttons - Bootstrap handles opening via data-bs-toggle
    const withdrawBtn = document.getElementById('withdrawBtn');
    if (withdrawBtn) {
        withdrawBtn.addEventListener('click', () => {
            loadWalletBalance(); // Refresh balance for available amount
        });
    }
    
    // Filter buttons
    document.querySelectorAll('[data-filter]').forEach(btn => {
        btn.addEventListener('click', function() {
            document.querySelectorAll('[data-filter]').forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            currentFilter = this.dataset.filter || 'all';
            currentPage = 1;
            loadTransactions();
        });
    });
    
    // Form submissions
    const topupForm = document.getElementById('topupForm');
    if (topupForm) {
        topupForm.addEventListener('submit', handleTopup);
    }
    
    const withdrawForm = document.getElementById('withdrawForm');
    if (withdrawForm) {
        withdrawForm.addEventListener('submit', handleWithdraw);
    }
    
    const transferForm = document.getElementById('transferForm');
    if (transferForm) {
        transferForm.addEventListener('submit', handleTransfer);
    }
    
    // Transfer modal - load balance when opened
    const transferModal = document.getElementById('transferModal');
    if (transferModal) {
        transferModal.addEventListener('show.bs.modal', function() {
            loadWalletBalance();
        });
    }
    
    // Check username when typing in transfer form
    const transferToUsername = document.getElementById('transferToUsername');
    if (transferToUsername) {
        let usernameCheckTimeout;
        transferToUsername.addEventListener('input', function() {
            clearTimeout(usernameCheckTimeout);
            const username = this.value.trim();
            const recipientInfo = document.getElementById('recipientInfo');
            const recipientName = document.getElementById('recipientName');
            
            if (username.length >= 3) {
                usernameCheckTimeout = setTimeout(() => {
                    checkUsername(username, recipientInfo, recipientName);
                }, 500);
            } else {
                if (recipientInfo) recipientInfo.style.display = 'none';
            }
        });
    }
    
    // Quick amount buttons
    document.querySelectorAll('.quick-amount-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const amountInput = document.getElementById('topupAmount');
            if (amountInput) {
                amountInput.value = this.dataset.amount;
            }
        });
    });
});

async function loadWalletBalance() {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Wallet?action=getBalance`);
        
        if (data && data.success !== false) {
            const balance = data.balance || 0;
            const totalEarned = data.total_earned || 0;
            const totalSpent = data.total_spent || 0;
            const frozenBalance = data.frozen_balance || 0;
            
            const balanceElement = document.getElementById('walletBalance');
            if (balanceElement) {
                const amountEl = balanceElement.querySelector('.amount');
                if (amountEl) {
                    amountEl.textContent = parseInt(balance).toLocaleString('ja-JP');
                }
            }
            
            const totalEarnedEl = document.getElementById('totalEarned');
            if (totalEarnedEl) {
                totalEarnedEl.textContent = KaruruUtils.formatPrice(totalEarned);
            }
            
            const totalSpentEl = document.getElementById('totalSpent');
            if (totalSpentEl) {
                totalSpentEl.textContent = KaruruUtils.formatPrice(totalSpent);
            }
            
            const frozenBalanceEl = document.getElementById('frozenBalance');
            if (frozenBalanceEl) {
                frozenBalanceEl.textContent = KaruruUtils.formatPrice(frozenBalance);
            }
            
            const availableBalanceEl = document.getElementById('availableBalance');
            if (availableBalanceEl) {
                availableBalanceEl.textContent = KaruruUtils.formatPrice(balance);
            }
            
            const availableBalanceForTransfer = document.getElementById('availableBalanceForTransfer');
            if (availableBalanceForTransfer) {
                const availableBalance = balance - frozenBalance;
                availableBalanceForTransfer.textContent = KaruruUtils.formatPrice(availableBalance);
            }
        }
    } catch (error) {
        console.error('Error loading wallet balance:', error);
    }
}

async function loadTransactions(page = 1) {
    try {
        const params = new URLSearchParams({
            action: 'getTransactions',
            page: page.toString(),
            limit: '20'
        });
        
        if (currentFilter && currentFilter !== 'all') {
            params.append('type', currentFilter);
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Wallet?${params}`);
        
        const container = document.getElementById('transactionsList');
        if (!container) return;
        
        if (data && data.success !== false) {
            const transactions = data.transactions || [];
            const totalPages = data.totalPages || data.total_pages || 1;
            
            if (transactions && transactions.length > 0) {
                renderTransactions(transactions);
                renderPagination(totalPages, page);
            } else {
                container.innerHTML = `
                    <div class="text-center py-5">
                        <i class="bi bi-inbox" style="font-size: 3rem; color: var(--text-muted); opacity: 0.5;"></i>
                        <p class="mt-3 text-muted">取引履歴がありません</p>
                    </div>
                `;
                document.getElementById('transactionsPagination').innerHTML = '';
            }
        } else {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3">取引履歴の読み込みに失敗しました</p>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading transactions:', error);
        const container = document.getElementById('transactionsList');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3">取引履歴の読み込みに失敗しました</p>
                    <small class="text-muted d-block mt-2">${error.message || ''}</small>
                </div>
            `;
        }
    }
}

function renderTransactions(transactions) {
    const container = document.getElementById('transactionsList');
    if (!container) return;
    
    if (transactions.length === 0) {
        container.innerHTML = `
            <div class="text-center py-5">
                <i class="bi bi-inbox" style="font-size: 3rem; color: var(--text-muted); opacity: 0.5;"></i>
                <p class="mt-3 text-muted">取引履歴がありません</p>
            </div>
        `;
        return;
    }
    
    container.innerHTML = transactions.map(transaction => {
        const type = transaction.transaction_type || transaction.type || 'unknown';
        const amount = transaction.amount || 0;
        const isCredit = type === 'credit' || type === 'deposit' || type === 'earning';
        const status = transaction.status || 'completed';
        
        return `
            <div class="transaction-item mb-3 p-3 rounded-3" style="background: var(--card-bg); border: 1px solid var(--border-color);">
                <div class="d-flex align-items-center">
                    <div class="transaction-icon me-3" style="width: 50px; height: 50px; background: ${isCredit ? 'rgba(13, 110, 253, 0.2)' : 'rgba(220, 53, 69, 0.2)'}; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 1.5rem;">
                        ${getTransactionIcon(type)}
                    </div>
                    <div class="flex-grow-1">
                        <h6 class="mb-1">${getTransactionTypeLabel(type)}</h6>
                        <small class="text-muted d-block">${KaruruUtils.formatDate(transaction.created_at)}</small>
                        ${transaction.description ? `<small class="text-muted d-block mt-1">${escapeHtml(transaction.description)}</small>` : ''}
                    </div>
                    <div class="text-end me-3">
                        <div class="fw-bold ${isCredit ? 'text-success' : 'text-danger'}" style="font-size: 1.1rem;">
                            ${isCredit ? '+' : '-'}${KaruruUtils.formatPrice(Math.abs(amount))}
                        </div>
                        <span class="badge ${getStatusBadgeClass(status)} mt-1">${getStatusLabel(status)}</span>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function getTransactionIcon(type) {
    const icons = {
        'credit': '💰',
        'deposit': '💰',
        'withdrawal': '💸',
        'withdraw': '💸',
        'debit': '💸',
        'purchase': '🛒',
        'earning': '💵',
        'refund': '↩️',
        'fee': '💳'
    };
    return icons[type] || '💼';
}

function getTransactionTypeLabel(type) {
    const labels = {
        'credit': '入金',
        'deposit': '入金',
        'withdrawal': '出金',
        'withdraw': '出金',
        'debit': '出金',
        'purchase': '購入',
        'earning': '収入',
        'refund': '返金',
        'fee': '手数料',
        'topup': 'チャージ'
    };
    return labels[type] || type;
}

function getStatusLabel(status) {
    const labels = {
        'completed': '完了',
        'pending': '処理中',
        'failed': '失敗',
        'cancelled': 'キャンセル'
    };
    return labels[status] || status;
}

function getStatusBadgeClass(status) {
    const classes = {
        'completed': 'bg-success',
        'pending': 'bg-warning',
        'failed': 'bg-danger',
        'cancelled': 'bg-secondary'
    };
    return classes[status] || 'bg-secondary';
}

function renderPagination(totalPages, currentPageNum) {
    const container = document.getElementById('transactionsPagination');
    if (!container) return;
    
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }
    
    container.innerHTML = '';
    
    // Previous button
    const prevBtn = document.createElement('li');
    prevBtn.className = `page-item ${currentPageNum === 1 ? 'disabled' : ''}`;
    prevBtn.innerHTML = `<a class="page-link" href="#" aria-label="Previous"><span aria-hidden="true">&laquo;</span></a>`;
    if (currentPageNum > 1) {
        prevBtn.querySelector('a').addEventListener('click', (e) => {
            e.preventDefault();
            currentPage = currentPageNum - 1;
            loadTransactions(currentPage);
        });
    }
    container.appendChild(prevBtn);
    
    // Page numbers
    for (let i = 1; i <= totalPages; i++) {
        if (i === 1 || i === totalPages || (i >= currentPageNum - 2 && i <= currentPageNum + 2)) {
            const pageItem = document.createElement('li');
            pageItem.className = `page-item ${i === currentPageNum ? 'active' : ''}`;
            const pageLink = document.createElement('a');
            pageLink.className = 'page-link';
            pageLink.href = '#';
            pageLink.textContent = i;
            pageLink.addEventListener('click', (e) => {
                e.preventDefault();
                currentPage = i;
                loadTransactions(i);
            });
            pageItem.appendChild(pageLink);
            container.appendChild(pageItem);
        } else if (i === currentPageNum - 3 || i === currentPageNum + 3) {
            const ellipsis = document.createElement('li');
            ellipsis.className = 'page-item disabled';
            ellipsis.innerHTML = '<span class="page-link">...</span>';
            container.appendChild(ellipsis);
        }
    }
    
    // Next button
    const nextBtn = document.createElement('li');
    nextBtn.className = `page-item ${currentPageNum === totalPages ? 'disabled' : ''}`;
    nextBtn.innerHTML = `<a class="page-link" href="#" aria-label="Next"><span aria-hidden="true">&raquo;</span></a>`;
    if (currentPageNum < totalPages) {
        nextBtn.querySelector('a').addEventListener('click', (e) => {
            e.preventDefault();
            currentPage = currentPageNum + 1;
            loadTransactions(currentPage);
        });
    }
    container.appendChild(nextBtn);
}

async function handleTopup(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('topupError');
    if (errorDiv) {
        errorDiv.classList.add('d-none');
        errorDiv.textContent = '';
    }
    
    const formData = new FormData(e.target);
    const submitBtn = e.target.querySelector('button[type="submit"]');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '処理中...';
    }
    
    try {
        const params = new URLSearchParams();
        params.append('action', 'topup');
        for (const [key, value] of formData.entries()) {
            params.append(key, value);
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Wallet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params.toString()
        });
        
        if (data && data.success !== false) {
            
            // Close modal using Bootstrap
            const modalElement = document.getElementById('topupModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement);
                if (modal) {
                    modal.hide();
                }
            }
            
            e.target.reset();
            loadWalletBalance();
            loadTransactions();
        } else {
            const errorMsg = data?.error || data?.message || 'チャージに失敗しました';
            if (errorDiv) {
                errorDiv.textContent = errorMsg;
                errorDiv.classList.remove('d-none');
            }
        }
    } catch (error) {
        console.error('Error:', error);
        const errorMsg = 'エラーが発生しました: ' + (error.message || '');
        if (errorDiv) {
            errorDiv.textContent = errorMsg;
            errorDiv.classList.remove('d-none');
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'チャージする';
        }
    }
}

async function handleWithdraw(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('withdrawError');
    if (errorDiv) {
        errorDiv.classList.add('d-none');
        errorDiv.textContent = '';
    }
    
    const formData = new FormData(e.target);
    const submitBtn = e.target.querySelector('button[type="submit"]');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '申請中...';
    }
    
    try {
        const params = new URLSearchParams();
        params.append('action', 'withdraw');
        for (const [key, value] of formData.entries()) {
            params.append(key, value);
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Wallet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params.toString()
        });
        
        if (data && data.success !== false) {
            
            // Close modal using Bootstrap
            const modalElement = document.getElementById('withdrawModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement);
                if (modal) {
                    modal.hide();
                }
            }
            
            e.target.reset();
            loadWalletBalance();
            loadTransactions();
        } else {
            const errorMsg = data?.error || data?.message || '出金申請に失敗しました';
            if (errorDiv) {
                errorDiv.textContent = errorMsg;
                errorDiv.classList.remove('d-none');
            }
        }
    } catch (error) {
        console.error('Error:', error);
        const errorMsg = 'エラーが発生しました: ' + (error.message || '');
        if (errorDiv) {
            errorDiv.textContent = errorMsg;
            errorDiv.classList.remove('d-none');
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = '出金申請';
        }
    }
}

async function handleTransfer(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('transferError');
    if (errorDiv) {
        errorDiv.classList.add('d-none');
        errorDiv.textContent = '';
    }
    
    const formData = new FormData(e.target);
    const submitBtn = e.target.querySelector('button[type="submit"]');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '送金中...';
    }
    
    try {
        const params = new URLSearchParams();
        params.append('action', 'transfer');
        for (const [key, value] of formData.entries()) {
            params.append(key, value);
        }
        
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/Wallet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params.toString()
        });
        
        if (data && data.success !== false) {
            
            // Close modal using Bootstrap
            const modalElement = document.getElementById('transferModal');
            if (modalElement) {
                const modal = bootstrap.Modal.getInstance(modalElement);
                if (modal) {
                    modal.hide();
                }
            }
            
            e.target.reset();
            const recipientInfo = document.getElementById('recipientInfo');
            if (recipientInfo) recipientInfo.style.display = 'none';
            
            loadWalletBalance();
            loadTransactions();
        } else {
            const errorMsg = data?.error || data?.message || '送金に失敗しました';
            if (errorDiv) {
                errorDiv.textContent = errorMsg;
                errorDiv.classList.remove('d-none');
            }
        }
    } catch (error) {
        console.error('Error:', error);
        const errorMsg = 'エラーが発生しました: ' + (error.message || '');
        if (errorDiv) {
            errorDiv.textContent = errorMsg;
            errorDiv.classList.remove('d-none');
        }
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = '送金する';
        }
    }
}

async function checkUsername(username, recipientInfo, recipientName) {
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProfileServlet?action=getUserByUsername&username=${encodeURIComponent(username)}`);
        
        if (data && data.success !== false && data.user) {
            if (recipientInfo && recipientName) {
                recipientName.textContent = `✓ ${data.user.username}${data.user.full_name ? ' (' + data.user.full_name + ')' : ''}`;
                recipientInfo.style.display = 'block';
            }
        } else {
            if (recipientInfo) {
                recipientInfo.style.display = 'none';
            }
        }
    } catch (error) {
        // Silently fail - username check is not critical
        if (recipientInfo) {
            recipientInfo.style.display = 'none';
        }
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
