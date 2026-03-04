// Admin dashboard JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadAdminStats();
    loadUsers();
    loadProducts();
    loadCategories();
    loadOrders();
    loadOffers();
    loadActivityLogs();
});

async function loadAdminStats() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getStats`;
        const data = await KaruruUtils.apiFetch(url);
        const stats = data.stats || data.data || data;
        
        if (stats) {
            // Update stat cards
            const totalUsersEl = document.getElementById('totalUsers');
            if (totalUsersEl) {
                const users = stats.totalUsers || 0;
                totalUsersEl.textContent = users.toLocaleString('ja-JP');
            }
            
            const totalProductsEl = document.getElementById('totalProducts');
            if (totalProductsEl) {
                const products = stats.totalProducts || 0;
                totalProductsEl.textContent = products.toLocaleString('ja-JP');
            }
            
            const totalOrdersEl = document.getElementById('totalOrders');
            if (totalOrdersEl) {
                const orders = stats.totalOrders || 0;
                totalOrdersEl.textContent = orders.toLocaleString('ja-JP');
            }
            
            const totalRevenueEl = document.getElementById('totalRevenue');
            if (totalRevenueEl) {
                const revenue = stats.totalRevenue || 0;
                const rounded = Math.round(Number(revenue));
                totalRevenueEl.textContent = typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice 
                    ? KaruruUtils.formatPrice(rounded) 
                    : '¥' + rounded.toLocaleString('ja-JP');
            }
            
            const totalOffersEl = document.getElementById('totalOffers');
            if (totalOffersEl) {
                totalOffersEl.textContent = (stats.totalOffers || 0).toLocaleString('ja-JP');
            }
            
            // Update statistics table
            updateStatisticsTable(stats);
        }
    } catch (error) {
        console.error('Error loading admin stats:', error);
        // Set default values on error
        const totalUsersEl = document.getElementById('totalUsers');
        if (totalUsersEl) totalUsersEl.textContent = '0';
        const totalProductsEl = document.getElementById('totalProducts');
        if (totalProductsEl) totalProductsEl.textContent = '0';
        const totalOrdersEl = document.getElementById('totalOrders');
        if (totalOrdersEl) totalOrdersEl.textContent = '0';
        const totalRevenueEl = document.getElementById('totalRevenue');
        if (totalRevenueEl) totalRevenueEl.textContent = '¥0';
        const totalOffersEl = document.getElementById('totalOffers');
        if (totalOffersEl) totalOffersEl.textContent = '0';
    }
}

function updateStatisticsTable(stats) {
    // Active Users
    const statActiveUsersEl = document.getElementById('statActiveUsers');
    if (statActiveUsersEl) {
        statActiveUsersEl.textContent = (stats.totalUsers || 0).toLocaleString('ja-JP');
    }
    
    // Active Products
    const statActiveProductsEl = document.getElementById('statActiveProducts');
    if (statActiveProductsEl) {
        statActiveProductsEl.textContent = (stats.totalProducts || 0).toLocaleString('ja-JP');
    }
    
    // Completed Orders (assuming all orders are completed for now)
    const statCompletedOrdersEl = document.getElementById('statCompletedOrders');
    if (statCompletedOrdersEl) {
        statCompletedOrdersEl.textContent = (stats.totalOrders || 0).toLocaleString('ja-JP');
    }
    
    // Average Order Value
    const statAvgOrderValueEl = document.getElementById('statAvgOrderValue');
    if (statAvgOrderValueEl) {
        const totalOrders = stats.totalOrders || 1;
        const totalRevenue = stats.totalRevenue || 0;
        const avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;
        statAvgOrderValueEl.textContent = '¥' + Math.round(avgOrderValue).toLocaleString('ja-JP');
    }
    
    // Average Rating (placeholder - would need to fetch from database)
    const statAvgRatingEl = document.getElementById('statAvgRating');
    if (statAvgRatingEl) {
        statAvgRatingEl.textContent = '4.5'; // Placeholder
    }
    
    // Update change badges with random positive values for demo
    const changeElements = [
        { id: 'statActiveUsersChange', value: '+12%' },
        { id: 'statActiveProductsChange', value: '+8%' },
        { id: 'statCompletedOrdersChange', value: '+15%' },
        { id: 'statAvgOrderValueChange', value: '+5%' },
        { id: 'statAvgRatingChange', value: '+2%' }
    ];
    
    changeElements.forEach(item => {
        const el = document.getElementById(item.id);
        if (el) {
            el.textContent = item.value;
        }
    });
}

async function loadUsers() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getUsers`;
        const data = await KaruruUtils.apiFetch(url);
        
        const container = document.getElementById('usersTable');
        if (!container) return;
        
        const users = KaruruUtils.extractData(data, 'users') || data.data || data.users || [];
        
        if (users && users.length > 0) {
            container.innerHTML = `
                <div class="table-responsive">
                    <table class="table table-light table-striped table-hover mb-0">
                        <thead>
                            <tr>
                                <th class="text-center fw-bold">ID</th>
                                <th class="fw-bold">ユーザー名</th>
                                <th class="fw-bold">メール</th>
                                <th class="text-center fw-bold">役割</th>
                                <th class="text-center fw-bold">認証</th>
                                <th class="text-center fw-bold">登録日</th>
                                <th class="text-center fw-bold" style="width: 150px;">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${users.map(user => `
                                <tr>
                                    <td class="text-center fw-bold">${user.user_id || 'N/A'}</td>
                                    <td>
                                        <i class="bi bi-person-circle me-2 text-primary"></i>
                                        ${user.username || 'N/A'}
                                    </td>
                                    <td>
                                        <i class="bi bi-envelope me-2 text-info"></i>
                                        ${user.email || 'N/A'}
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${getRoleBadgeClass(user.role)}">${user.role || 'user'}</span>
                                    </td>
                                    <td class="text-center">
                                        ${user.is_verified ? '<i class="bi bi-check-circle-fill text-success fs-5"></i>' : '<i class="bi bi-x-circle text-danger fs-5"></i>'}
                                    </td>
                                    <td class="text-center">
                                        ${user.created_at ? new Date(user.created_at).toLocaleDateString('ja-JP') : 'N/A'}
                                    </td>
                                    <td class="text-center">
                                        <button class="btn btn-sm btn-primary me-1" 
                                                onclick="editUser(${user.user_id})" 
                                                title="編集">
                                            <i class="bi bi-pencil"></i>
                                        </button>
                                        <button class="btn btn-sm btn-danger" 
                                                onclick="deleteUser(${user.user_id})" 
                                                title="削除">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-dark border-top border-secondary p-3">
                    <button class="btn btn-primary" onclick="showUserModal()">
                        <i class="bi bi-plus-circle me-2"></i>新しいユーザーを追加
                    </button>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center p-4 text-muted">
                    <i class="bi bi-people" style="font-size: 3rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">ユーザーが見つかりませんでした。</p>
                    <button class="btn btn-primary mt-3" onclick="showUserModal()">
                        <i class="bi bi-plus-circle me-2"></i>最初のユーザーを追加
                    </button>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading users:', error);
        const container = document.getElementById('usersTable');
        if (container) {
            container.innerHTML = '<div class="text-center p-4 text-danger">ユーザーの読み込みに失敗しました。</div>';
        }
    }
}

function getRoleBadgeClass(role) {
    switch(role) {
        case 'admin': return 'bg-danger';
        case 'seller': return 'bg-warning';
        case 'moderator': return 'bg-info';
        default: return 'bg-secondary';
    }
}

async function loadProducts() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getProducts`;
        const data = await KaruruUtils.apiFetch(url);
        
        const container = document.getElementById('productsTable');
        if (!container) return;
        
        const products = KaruruUtils.extractData(data, 'products') || data.data || data.products || [];
        
        if (products && products.length > 0) {
            container.innerHTML = `
                <div class="table-responsive">
                    <table class="table table-light table-striped table-hover mb-0">
                        <thead>
                            <tr>
                                <th class="text-center fw-bold">ID</th>
                                <th class="fw-bold">商品名</th>
                                <th class="text-end fw-bold">価格</th>
                                <th class="text-center fw-bold">在庫</th>
                                <th class="text-center fw-bold">ステータス</th>
                                <th class="text-center fw-bold">作成日</th>
                                <th class="text-center fw-bold" style="width: 150px;">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${products.map(product => `
                                <tr>
                                    <td class="text-center fw-bold">${product.product_id || 'N/A'}</td>
                                    <td>
                                        <i class="bi bi-box-seam me-2 text-success"></i>
                                        ${product.product_name || 'N/A'}
                                    </td>
                                    <td class="text-end fw-bold text-warning">
                                        ${typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice 
                                            ? KaruruUtils.formatPrice(product.price) 
                                            : '¥' + (product.price || 0).toLocaleString('ja-JP')}
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${product.stock_quantity > 0 ? 'bg-success' : 'bg-danger'}">
                                            ${product.stock_quantity || 0}
                                        </span>
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${getStatusBadgeClass(product.status)}">
                                            ${product.status || 'N/A'}
                                        </span>
                                    </td>
                                    <td class="text-center">
                                        ${product.created_at ? new Date(product.created_at).toLocaleDateString('ja-JP') : 'N/A'}
                                    </td>
                                    <td class="text-center">
                                        <button class="btn btn-sm btn-primary me-1" 
                                                onclick="editProduct(${product.product_id})" 
                                                title="編集">
                                            <i class="bi bi-pencil"></i>
                                        </button>
                                        <button class="btn btn-sm btn-danger" 
                                                onclick="deleteProduct(${product.product_id})" 
                                                title="削除">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-dark border-top border-secondary p-3">
                    <button class="btn btn-success" onclick="showProductModal()">
                        <i class="bi bi-plus-circle me-2"></i>新しい商品を追加
                    </button>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center p-4 text-muted">
                    <i class="bi bi-box-seam" style="font-size: 3rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">商品が見つかりませんでした。</p>
                    <button class="btn btn-success mt-3" onclick="showProductModal()">
                        <i class="bi bi-plus-circle me-2"></i>最初の商品を追加
                    </button>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading products:', error);
        const container = document.getElementById('productsTable');
        if (container) {
            container.innerHTML = '<div class="text-center p-4 text-danger">商品の読み込みに失敗しました。</div>';
        }
    }
}

function getStatusBadgeClass(status) {
    switch(status) {
        case 'available': return 'bg-success';
        case 'sold': return 'bg-danger';
        case 'rented': return 'bg-info';
        case 'reserved': return 'bg-warning';
        default: return 'bg-secondary';
    }
}

async function loadOrders() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getOrders`;
        const data = await KaruruUtils.apiFetch(url);
        
        const container = document.getElementById('ordersTable');
        if (!container) return;
        
        const orders = KaruruUtils.extractData(data, 'orders') || data.data || data.orders || [];
        
        if (orders && orders.length > 0) {
            container.innerHTML = `
                <div class="table-responsive">
                    <table class="table table-light table-striped table-hover mb-0">
                        <thead>
                            <tr>
                                <th class="fw-bold">注文番号</th>
                                <th class="text-center fw-bold">ユーザー名</th>
                                <th class="text-end fw-bold">合計金額</th>
                                <th class="text-center fw-bold">支払い</th>
                                <th class="text-center fw-bold">ステータス</th>
                                <th class="text-center fw-bold">作成日</th>
                                <th class="text-center fw-bold" style="width: 150px;">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${orders.map(order => `
                                <tr>
                                    <td>
                                        <i class="bi bi-receipt me-2 text-warning"></i>
                                        <strong>${order.order_number || 'N/A'}</strong>
                                    </td>
                                    <td class="text-center">
                                        <span class="badge bg-info">${order.username || order.user_id || 'N/A'}</span>
                                    </td>
                                    <td class="text-end fw-bold text-success">
                                        ${typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice 
                                            ? KaruruUtils.formatPrice(order.total_amount) 
                                            : '¥' + (order.total_amount || 0).toLocaleString('ja-JP')}
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${getPaymentStatusBadgeClass(order.payment_status)}">
                                            ${order.payment_status || 'N/A'}
                                        </span>
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${getOrderStatusBadgeClass(order.order_status)}">
                                            ${order.order_status || 'N/A'}
                                        </span>
                                    </td>
                                    <td class="text-center">
                                        ${order.created_at ? new Date(order.created_at).toLocaleDateString('ja-JP') : 'N/A'}
                                    </td>
                                    <td class="text-center">
                                        <button class="btn btn-sm btn-primary me-1" 
                                                onclick="editOrder(${order.order_id})" 
                                                title="編集">
                                            <i class="bi bi-pencil"></i>
                                        </button>
                                        <button class="btn btn-sm btn-danger" 
                                                onclick="deleteOrder(${order.order_id})" 
                                                title="削除">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-dark border-top border-secondary p-3">
                    <button class="btn btn-warning" onclick="showOrderModal()">
                        <i class="bi bi-plus-circle me-2"></i>新しい注文を追加
                    </button>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center p-4 text-muted">
                    <i class="bi bi-receipt" style="font-size: 3rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">注文が見つかりませんでした。</p>
                    <button class="btn btn-warning mt-3" onclick="showOrderModal()">
                        <i class="bi bi-plus-circle me-2"></i>最初の注文を追加
                    </button>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading orders:', error);
        const container = document.getElementById('ordersTable');
        if (container) {
            container.innerHTML = '<div class="text-center p-4 text-danger">注文の読み込みに失敗しました。</div>';
        }
    }
}

function getPaymentStatusBadgeClass(status) {
    switch(status) {
        case 'paid': return 'bg-success';
        case 'pending': return 'bg-warning';
        case 'failed': return 'bg-danger';
        case 'refunded': return 'bg-info';
        default: return 'bg-secondary';
    }
}

function getOfferStatusBadgeClass(status) {
    switch(status) {
        case 'pending': return 'bg-warning';
        case 'accepted': return 'bg-success';
        case 'rejected': return 'bg-danger';
        case 'cancelled': return 'bg-secondary';
        default: return 'bg-secondary';
    }
}

async function loadOffers() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getOffers`;
        const data = await KaruruUtils.apiFetch(url);
        
        const container = document.getElementById('offersTable');
        if (!container) return;
        
        const offers = KaruruUtils.extractData(data, 'offers') || data.data || data.offers || [];
        
        if (offers && offers.length > 0) {
            container.innerHTML = `
                <div class="table-responsive">
                    <table class="table table-light table-striped table-hover mb-0">
                        <thead>
                            <tr>
                                <th class="text-center fw-bold">ID</th>
                                <th class="fw-bold">商品</th>
                                <th class="fw-bold">買い手</th>
                                <th class="fw-bold">売り手</th>
                                <th class="text-end fw-bold">オファー価格</th>
                                <th class="text-center fw-bold">ステータス</th>
                                <th class="text-center fw-bold">日付</th>
                                <th class="text-center fw-bold" style="width: 120px;">操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${offers.map(offer => `
                                <tr>
                                    <td class="text-center fw-bold">${offer.offer_id || 'N/A'}</td>
                                    <td>
                                        <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${offer.product_id}" class="text-decoration-none" target="_blank">
                                            <i class="bi bi-box-seam me-2 text-success"></i>
                                            ${offer.product_name || 'N/A'}
                                        </a>
                                        <br><small class="text-muted">定価: ${typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice 
                                            ? KaruruUtils.formatPrice(offer.product_price || 0) : '¥' + (offer.product_price || 0)}</small>
                                    </td>
                                    <td>
                                        <i class="bi bi-person me-2 text-info"></i>
                                        ${offer.buyer_username || offer.buyer_name || 'N/A'}
                                    </td>
                                    <td>
                                        <i class="bi bi-shop me-2 text-primary"></i>
                                        ${offer.seller_username || offer.seller_name || 'N/A'}
                                    </td>
                                    <td class="text-end fw-bold text-warning">
                                        ${typeof KaruruUtils !== 'undefined' && KaruruUtils.formatPrice 
                                            ? KaruruUtils.formatPrice(offer.offer_price) 
                                            : '¥' + (offer.offer_price || 0).toLocaleString('ja-JP')}
                                    </td>
                                    <td class="text-center">
                                        <span class="badge ${getOfferStatusBadgeClass(offer.status)}">
                                            ${offer.status === 'pending' ? '保留中' : 
                                              offer.status === 'accepted' ? '承認済み' : 
                                              offer.status === 'rejected' ? '拒否' : 
                                              offer.status === 'cancelled' ? 'キャンセル' : offer.status || 'N/A'}
                                        </span>
                                    </td>
                                    <td class="text-center">
                                        ${offer.created_at ? new Date(offer.created_at).toLocaleDateString('ja-JP') : 'N/A'}
                                    </td>
                                    <td class="text-center">
                                        <a href="${window.CONTEXT_PATH}/offers.jsp" class="btn btn-sm btn-primary me-1" title="オファー詳細">
                                            <i class="bi bi-eye"></i>
                                        </a>
                                        <button class="btn btn-sm btn-danger" 
                                                onclick="deleteOffer(${offer.offer_id})" 
                                                title="削除">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="card-footer bg-dark border-top border-secondary p-3">
                    <a href="${window.CONTEXT_PATH}/offers.jsp" class="btn btn-warning">
                        <i class="bi bi-hand-thumbs-up me-2"></i>オファー管理ページへ
                    </a>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center p-4 text-muted">
                    <i class="bi bi-hand-thumbs-up" style="font-size: 3rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">オファーが見つかりませんでした。</p>
                    <a href="${window.CONTEXT_PATH}/offers.jsp" class="btn btn-warning mt-3">
                        <i class="bi bi-hand-thumbs-up me-2"></i>オファー管理ページへ
                    </a>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading offers:', error);
        const container = document.getElementById('offersTable');
        if (container) {
            container.innerHTML = '<div class="text-center p-4 text-danger">オファーの読み込みに失敗しました。</div>';
        }
    }
}

function deleteOffer(offerId) {
    if (!confirm('このオファーを削除してもよろしいですか？')) return;
    
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=delete&table=offers&id=${offerId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadOffers();
            loadAdminStats();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('オファーを削除しました', 'success');
            } else {
                alert('オファーが削除されました。');
            }
        } else {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification(data.message || '削除に失敗しました', 'danger');
            }
        }
    })
    .catch(error => {
        console.error('Error deleting offer:', error);
    });
}

function getOrderStatusBadgeClass(status) {
    switch(status) {
        case 'delivered': return 'bg-success';
        case 'shipped': return 'bg-info';
        case 'processing': return 'bg-primary';
        case 'confirmed': return 'bg-warning';
        case 'cancelled': return 'bg-danger';
        case 'pending': return 'bg-secondary';
        default: return 'bg-secondary';
    }
}

async function loadCategories() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getCategories`;
        const data = await KaruruUtils.apiFetch(url);
        
        const container = document.getElementById('categoriesTable');
        if (!container) return;
        
        const categories = KaruruUtils.extractData(data, 'categories') || data.data || data.categories || [];
        
         if (categories && categories.length > 0) {
             container.innerHTML = `
                 <div class="table-responsive">
                     <table class="table table-light table-striped table-hover mb-0">
                         <thead class="table-light">
                             <tr>
                                 <th class="text-center fw-bold" style="width: 80px;">画像</th>
                                 <th class="text-center fw-bold">ID</th>
                                 <th class="fw-bold">カテゴリー名</th>
                                 <th class="fw-bold">スラッグ</th>
                                 <th class="text-center fw-bold">表示順</th>
                                 <th class="text-center fw-bold">ステータス</th>
                                 <th class="text-center fw-bold">親カテゴリー</th>
                                 <th class="text-center fw-bold" style="width: 150px;">操作</th>
                             </tr>
                         </thead>
                         <tbody>
                             ${categories.map(category => {
                                 const slug = category.slug || '';
                                 
                                 // Priority: 1. image_url from database, 2. slug-based path from /img/categories/, 3. default
                                 let categoryImage = '';
                                 
                                 // First, try image_url from database
                                 if (category.image_url && category.image_url.trim() !== '' && category.image_url !== 'null') {
                                     categoryImage = category.image_url;
                                 } 
                                 // Second, try slug-based path from /img/categories/
                                 else if (slug && slug.trim() !== '') {
                                     categoryImage = `/img/categories/${slug}.png`;
                                 }
                                 
                                 // Resolve full URL using utility function with default fallback
                                 const imagePath = categoryImage ? 
                                     KaruruUtils.resolveImageUrl(categoryImage, '/img/default-category.png') : 
                                     KaruruUtils.resolveImageUrl('', '/img/default-category.png');
                                 
                                 return `
                                 <tr>
                                     <td class="text-center">
                                         <img src="${imagePath}" 
                                              alt="${escapeHtml(category.category_name || '')}" 
                                              class="img-thumbnail" 
                                              style="width: 60px; height: 60px; object-fit: cover; border-color: var(--border-color);"
                                              onerror="this.onerror=null; this.src='${KaruruUtils.resolveImageUrl('', '/img/default-category.png')}';">
                                     </td>
                                     <td class="text-center fw-bold">${category.category_id || 'N/A'}</td>
                                     <td>
                                         <i class="bi bi-tag me-2 text-primary"></i>
                                         <strong>${escapeHtml(category.category_name || 'N/A')}</strong>
                                     </td>
                                     <td>
                                         <code class="text-info px-2 py-1 rounded" style="background-color: var(--bg-secondary);">${escapeHtml(category.slug || 'N/A')}</code>
                                     </td>
                                     <td class="text-center">
                                         <span class="badge bg-secondary">${category.display_order || 0}</span>
                                     </td>
                                     <td class="text-center">
                                         <span class="badge ${category.is_active ? 'bg-success' : 'bg-danger'}">
                                             ${category.is_active ? 'アクティブ' : '非アクティブ'}
                                         </span>
                                     </td>
                                     <td class="text-center">
                                         ${category.parent_name && category.parent_name !== 'No Parent' ? 
                                             `<span class="badge bg-info">${escapeHtml(category.parent_name)}</span>` : 
                                             '<span class="text-muted">-</span>'}
                                     </td>
                                     <td class="text-center">
                                         <button class="btn btn-sm btn-primary me-1" 
                                                 onclick="editCategory(${category.category_id})" 
                                                 title="編集">
                                             <i class="bi bi-pencil"></i>
                                         </button>
                                         <button class="btn btn-sm btn-danger" 
                                                 onclick="deleteCategory(${category.category_id})" 
                                                 title="削除">
                                             <i class="bi bi-trash"></i>
                                         </button>
                                     </td>
                                 </tr>
                             `;
                             }).join('')}
                         </tbody>
                     </table>
                 </div>
                 <div class="card-footer bg-dark border-top border-secondary p-3">
                     <button class="btn btn-primary" onclick="showAddCategoryModal()">
                         <i class="bi bi-plus-circle me-2"></i>新しいカテゴリーを追加
                     </button>
                 </div>
             `;
        } else {
            container.innerHTML = `
                <div class="text-center p-4 text-muted">
                    <i class="bi bi-tags" style="font-size: 3rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">カテゴリーが見つかりませんでした。</p>
                    <button class="btn btn-primary mt-3" onclick="showAddCategoryModal()">
                        <i class="bi bi-plus-circle me-2"></i>最初のカテゴリーを追加
                    </button>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading categories:', error);
        const container = document.getElementById('categoriesTable');
        if (container) {
            container.innerHTML = '<div class="text-center p-4 text-danger">カテゴリーの読み込みに失敗しました。</div>';
        }
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showAddCategoryModal() {
    // Create modal for adding/editing category
    const modalHtml = `
        <div class="modal fade" id="categoryModal" tabindex="-1" aria-labelledby="categoryModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title" id="categoryModalLabel">
                            <i class="bi bi-tag me-2"></i>カテゴリーを追加
                        </h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <form id="categoryForm">
                            <input type="hidden" id="categoryId" name="category_id">
                            <div class="mb-3">
                                <label for="categoryName" class="form-label">カテゴリー名 *</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="categoryName" name="category_name" required>
                            </div>
                            <div class="mb-3">
                                <label for="categorySlug" class="form-label">スラッグ *</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="categorySlug" name="slug" required>
                                <small class="text-muted">例: electronics, fashion</small>
                            </div>
                            <div class="mb-3">
                                <label for="categoryDescription" class="form-label">説明</label>
                                <textarea class="form-control form-control-light" 
                                          id="categoryDescription" name="description" rows="3"></textarea>
                            </div>
                            <div class="mb-3">
                                <label for="categoryImageUrl" class="form-label">画像URL</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="categoryImageUrl" name="image_url" 
                                       placeholder="img/categories/category-name.jpg">
                                <small class="text-muted">パス: img/categories/ から始まる</small>
                                <div class="mt-2">
                                    <img id="categoryImagePreview" src="" alt="Preview" 
                                         class="img-thumbnail d-none" 
                                         style="max-width: 200px; max-height: 200px; object-fit: cover;">
                                </div>
                            </div>
                            <div class="mb-3">
                                <label for="categoryIconUrl" class="form-label">アイコンURL</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="categoryIconUrl" name="icon_url">
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="displayOrder" class="form-label">表示順</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="displayOrder" name="display_order" value="0" min="0">
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="parentId" class="form-label">親カテゴリーID</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="parentId" name="parent_id" min="0">
                                </div>
                            </div>
                            <div class="mb-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="isActive" name="is_active" checked>
                                    <label class="form-check-label" for="isActive">
                                        アクティブ
                                    </label>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="saveCategory()">
                            <i class="bi bi-save me-2"></i>保存
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Remove existing modal if any
    const existingModal = document.getElementById('categoryModal');
    if (existingModal) {
        existingModal.remove();
    }
    
    // Add modal to body
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    
    // Initialize Bootstrap modal
    const modal = new bootstrap.Modal(document.getElementById('categoryModal'));
    modal.show();
    
    // Setup image preview
    const imageUrlInput = document.getElementById('categoryImageUrl');
    const imagePreview = document.getElementById('categoryImagePreview');
    
    if (imageUrlInput && imagePreview) {
        imageUrlInput.addEventListener('input', function() {
            const url = this.value.trim();
            if (url) {
                const fullUrl = url.startsWith('http') ? url : 
                               url.startsWith('/') ? `${window.CONTEXT_PATH}${url}` : 
                               `${window.CONTEXT_PATH}/img/categories/${url}`;
                imagePreview.src = fullUrl;
                imagePreview.classList.remove('d-none');
                imagePreview.onerror = function() {
                    this.classList.add('d-none');
                };
            } else {
                imagePreview.classList.add('d-none');
            }
        });
    }
    
    // Clean up on hide
    document.getElementById('categoryModal').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

function editCategory(categoryId) {
    // Fetch category data
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=getRecord&table=categories&id=${categoryId}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.record) {
                const category = data.record;
                
                // Show modal first
                showAddCategoryModal();
                
                // Wait for modal to be shown, then populate
                setTimeout(() => {
                    document.getElementById('categoryModalLabel').innerHTML = 
                        '<i class="bi bi-pencil me-2"></i>カテゴリーを編集';
                    document.getElementById('categoryId').value = category.category_id || '';
                    document.getElementById('categoryName').value = category.category_name || '';
                    document.getElementById('categorySlug').value = category.slug || '';
                    document.getElementById('categoryDescription').value = category.description || '';
                    document.getElementById('categoryImageUrl').value = category.image_url || '';
                    document.getElementById('categoryIconUrl').value = category.icon_url || '';
                    document.getElementById('displayOrder').value = category.display_order || 0;
                    document.getElementById('parentId').value = category.parent_id || '';
                    document.getElementById('isActive').checked = category.is_active !== false;
                    
                    // Trigger image preview
                    const imageUrlInput = document.getElementById('categoryImageUrl');
                    if (imageUrlInput && imageUrlInput.value) {
                        imageUrlInput.dispatchEvent(new Event('input'));
                    }
                }, 300);
            } else {
            }
        })
        .catch(error => {
            console.error('Error fetching category:', error);
        });
}

function saveCategory() {
    const form = document.getElementById('categoryForm');
    if (!form) return;
    
    const formData = new FormData(form);
    const categoryId = formData.get('category_id');
    
    // Build JSON object
    const categoryData = {
        category_name: formData.get('category_name'),
        slug: formData.get('slug'),
        description: formData.get('description') || null,
        image_url: formData.get('image_url') || null,
        icon_url: formData.get('icon_url') || null,
        display_order: parseInt(formData.get('display_order')) || 0,
        parent_id: formData.get('parent_id') ? parseInt(formData.get('parent_id')) : null,
        is_active: formData.get('is_active') === 'on'
    };
    
    // Ensure image_url uses img/categories path if provided
    if (categoryData.image_url) {
        const imageUrl = categoryData.image_url.trim();
        // If it's just a filename or doesn't start with http, /, or img/, prepend img/categories/
        if (imageUrl && !imageUrl.startsWith('http') && 
            !imageUrl.startsWith('/') && !imageUrl.startsWith('img/')) {
            categoryData.image_url = 'img/categories/' + imageUrl;
        } else if (imageUrl) {
            categoryData.image_url = imageUrl;
        }
    }
    
    const url = categoryId ? 
        `${window.CONTEXT_PATH}/AdminServlet?action=update&table=categories&id=${categoryId}` :
        `${window.CONTEXT_PATH}/AdminServlet?action=create&table=categories`;
    
    const method = categoryId ? 'POST' : 'POST';
    
    fetch(url, {
        method: method,
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(categoryData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('categoryModal'));
            if (modal) modal.hide();
            
            // Reload categories
            loadCategories();
            
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('カテゴリーを保存しました', 'success');
            } else {
                alert('カテゴリーが正常に保存されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error saving category:', error);
    });
}

function deleteCategory(categoryId) {
    if (!confirm('このカテゴリーを削除してもよろしいですか？')) {
        return;
    }
    
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=delete&table=categories&id=${categoryId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadCategories();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('カテゴリーを削除しました', 'success');
            } else {
                alert('カテゴリーが削除されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error deleting category:', error);
    });
}

// ==================== USER MANAGEMENT ====================

function editUser(userId) {
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=getRecord&table=users&id=${userId}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.record) {
                const user = data.record;
                showUserModal(user);
            } else {
            }
        })
        .catch(error => {
            console.error('Error fetching user:', error);
        });
}

function showUserModal(user = null) {
    const isEdit = user !== null;
    const modalHtml = `
        <div class="modal fade" id="userModal" tabindex="-1" aria-labelledby="userModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title" id="userModalLabel">
                            <i class="bi bi-person me-2"></i>${isEdit ? 'ユーザーを編集' : 'ユーザーを追加'}
                        </h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <form id="userForm">
                            <input type="hidden" id="userId" name="user_id" value="${user?.user_id || ''}">
                            <div class="mb-3">
                                <label for="username" class="form-label">ユーザー名 *</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="username" name="username" value="${user?.username || ''}" required>
                            </div>
                            <div class="mb-3">
                                <label for="email" class="form-label">メール *</label>
                                <input type="email" class="form-control bg-dark text-light border-secondary" 
                                       id="email" name="email" value="${user?.email || ''}" required>
                            </div>
                            <div class="mb-3">
                                <label for="role" class="form-label">役割 *</label>
                                <select class="form-select form-control-light" id="role" name="role" required>
                                    <option value="user" ${user?.role === 'user' ? 'selected' : ''}>ユーザー</option>
                                    <option value="seller" ${user?.role === 'seller' ? 'selected' : ''}>セラー</option>
                                    <option value="moderator" ${user?.role === 'moderator' ? 'selected' : ''}>モデレーター</option>
                                    <option value="admin" ${user?.role === 'admin' ? 'selected' : ''}>管理者</option>
                                </select>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <div class="form-check">
                                        <input class="form-check-input" type="checkbox" id="isVerified" name="is_verified" ${user?.is_verified ? 'checked' : ''}>
                                        <label class="form-check-label" for="isVerified">認証済み</label>
                                    </div>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <div class="form-check">
                                        <input class="form-check-input" type="checkbox" id="isSeller" name="is_seller" ${user?.is_seller ? 'checked' : ''}>
                                        <label class="form-check-label" for="isSeller">セラー</label>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="saveUser()">
                            <i class="bi bi-save me-2"></i>保存
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    const existingModal = document.getElementById('userModal');
    if (existingModal) existingModal.remove();
    
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    const modal = new bootstrap.Modal(document.getElementById('userModal'));
    modal.show();
    
    document.getElementById('userModal').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

function saveUser() {
    const form = document.getElementById('userForm');
    if (!form) return;
    
    const formData = new FormData(form);
    const userId = formData.get('user_id');
    
    const userData = {
        username: formData.get('username'),
        email: formData.get('email'),
        role: formData.get('role'),
        is_verified: formData.get('is_verified') === 'on',
        is_seller: formData.get('is_seller') === 'on'
    };
    
    const url = userId ? 
        `${window.CONTEXT_PATH}/AdminServlet?action=update&table=users&id=${userId}` :
        `${window.CONTEXT_PATH}/AdminServlet?action=create&table=users`;
    
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            bootstrap.Modal.getInstance(document.getElementById('userModal')).hide();
            loadUsers();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('ユーザーを保存しました', 'success');
            } else {
                alert('ユーザーが正常に保存されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error saving user:', error);
    });
}

function deleteUser(userId) {
    if (!confirm('このユーザーを削除してもよろしいですか？')) return;
    
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=delete&table=users&id=${userId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadUsers();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('ユーザーを削除しました', 'success');
            } else {
                alert('ユーザーが削除されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error deleting user:', error);
    });
}

// ==================== PRODUCT MANAGEMENT ====================

function editProduct(productId) {
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=getRecord&table=products&id=${productId}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.record) {
                const product = data.record;
                showProductModal(product);
            } else {
            }
        })
        .catch(error => {
            console.error('Error fetching product:', error);
        });
}

function showProductModal(product = null) {
    const isEdit = product !== null;
    const modalHtml = `
        <div class="modal fade" id="productModal" tabindex="-1" aria-labelledby="productModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title" id="productModalLabel">
                            <i class="bi bi-box-seam me-2"></i>${isEdit ? '商品を編集' : '商品を追加'}
                        </h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <form id="productForm">
                            <input type="hidden" id="productId" name="product_id" value="${product?.product_id || ''}">
                            <div class="mb-3">
                                <label for="productName" class="form-label">商品名 *</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="productName" name="product_name" value="${product?.product_name || ''}" required>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="productPrice" class="form-label">価格 *</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="productPrice" name="price" value="${product?.price || ''}" min="0" step="0.01" required>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="stockQuantity" class="form-label">在庫数 *</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="stockQuantity" name="stock_quantity" value="${product?.stock_quantity || ''}" min="0" required>
                                </div>
                            </div>
                            <div class="mb-3">
                                <label for="productStatus" class="form-label">ステータス *</label>
                                <select class="form-select form-control-light" id="productStatus" name="status" required>
                                    <option value="available" ${product?.status === 'available' ? 'selected' : ''}>利用可能</option>
                                    <option value="sold" ${product?.status === 'sold' ? 'selected' : ''}>売却済み</option>
                                    <option value="rented" ${product?.status === 'rented' ? 'selected' : ''}>レンタル中</option>
                                    <option value="reserved" ${product?.status === 'reserved' ? 'selected' : ''}>予約済み</option>
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="productCondition" class="form-label">状態</label>
                                <select class="form-select form-control-light" id="productCondition" name="condition">
                                    <option value="new" ${product?.condition === 'new' ? 'selected' : ''}>新品</option>
                                    <option value="like_new" ${product?.condition === 'like_new' ? 'selected' : ''}>新品同様</option>
                                    <option value="good" ${product?.condition === 'good' ? 'selected' : ''}>良好</option>
                                    <option value="fair" ${product?.condition === 'fair' ? 'selected' : ''}>普通</option>
                                </select>
                            </div>
                            <div class="mb-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="isRental" name="is_rental" ${product?.is_rental ? 'checked' : ''}>
                                    <label class="form-check-label" for="isRental">レンタル可能</label>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="saveProduct()">
                            <i class="bi bi-save me-2"></i>保存
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    const existingModal = document.getElementById('productModal');
    if (existingModal) existingModal.remove();
    
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    const modal = new bootstrap.Modal(document.getElementById('productModal'));
    modal.show();
    
    document.getElementById('productModal').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

function saveProduct() {
    const form = document.getElementById('productForm');
    if (!form) return;
    
    const formData = new FormData(form);
    const productId = formData.get('product_id');
    
    const productData = {
        product_name: formData.get('product_name'),
        price: parseFloat(formData.get('price')) || 0,
        stock_quantity: parseInt(formData.get('stock_quantity')) || 0,
        status: formData.get('status'),
        condition: formData.get('condition') || null,
        is_rental: formData.get('is_rental') === 'on'
    };
    
    const url = productId ? 
        `${window.CONTEXT_PATH}/AdminServlet?action=update&table=products&id=${productId}` :
        `${window.CONTEXT_PATH}/AdminServlet?action=create&table=products`;
    
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(productData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            bootstrap.Modal.getInstance(document.getElementById('productModal')).hide();
            loadProducts();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('商品を保存しました', 'success');
            } else {
                alert('商品が正常に保存されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error saving product:', error);
    });
}

function deleteProduct(productId) {
    if (!confirm('この商品を削除してもよろしいですか？')) return;
    
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=delete&table=products&id=${productId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadProducts();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('商品を削除しました', 'success');
            } else {
                alert('商品が削除されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error deleting product:', error);
    });
}

// ==================== ORDER MANAGEMENT ====================

function editOrder(orderId) {
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=getRecord&table=orders&id=${orderId}`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.record) {
                const order = data.record;
                showOrderModal(order);
            } else {
            }
        })
        .catch(error => {
            console.error('Error fetching order:', error);
        });
}

function showOrderModal(order = null) {
    const isEdit = order !== null;
    const modalHtml = `
        <div class="modal fade" id="orderModal" tabindex="-1" aria-labelledby="orderModalLabel" aria-hidden="true">
            <div class="modal-dialog modal-lg">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title" id="orderModalLabel">
                            <i class="bi bi-receipt me-2"></i>${isEdit ? '注文を編集' : '注文を追加'}
                        </h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <form id="orderForm">
                            <input type="hidden" id="orderId" name="order_id" value="${order?.order_id || ''}">
                            <div class="mb-3">
                                <label for="orderNumber" class="form-label">注文番号 *</label>
                                <input type="text" class="form-control form-control-light" 
                                       id="orderNumber" name="order_number" value="${order?.order_number || ''}" required>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="orderTotalAmount" class="form-label">合計金額 *</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="orderTotalAmount" name="total_amount" value="${order?.total_amount || ''}" min="0" step="0.01" required>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="orderUserId" class="form-label">ユーザーID</label>
                                    <input type="number" class="form-control form-control-light" 
                                           id="orderUserId" name="user_id" value="${order?.user_id || ''}" min="1">
                                </div>
                            </div>
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="paymentStatus" class="form-label">支払いステータス *</label>
                                    <select class="form-select form-control-light" id="paymentStatus" name="payment_status" required>
                                        <option value="pending" ${order?.payment_status === 'pending' ? 'selected' : ''}>保留中</option>
                                        <option value="paid" ${order?.payment_status === 'paid' ? 'selected' : ''}>支払済み</option>
                                        <option value="failed" ${order?.payment_status === 'failed' ? 'selected' : ''}>失敗</option>
                                        <option value="refunded" ${order?.payment_status === 'refunded' ? 'selected' : ''}>返金済み</option>
                                    </select>
                                </div>
                                <div class="col-md-6 mb-3">
                                    <label for="orderStatus" class="form-label">注文ステータス *</label>
                                    <select class="form-select form-control-light" id="orderStatus" name="order_status" required>
                                        <option value="pending" ${order?.order_status === 'pending' ? 'selected' : ''}>保留中</option>
                                        <option value="confirmed" ${order?.order_status === 'confirmed' ? 'selected' : ''}>確認済み</option>
                                        <option value="processing" ${order?.order_status === 'processing' ? 'selected' : ''}>処理中</option>
                                        <option value="shipped" ${order?.order_status === 'shipped' ? 'selected' : ''}>発送済み</option>
                                        <option value="delivered" ${order?.order_status === 'delivered' ? 'selected' : ''}>配達済み</option>
                                        <option value="cancelled" ${order?.order_status === 'cancelled' ? 'selected' : ''}>キャンセル</option>
                                    </select>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer" style="border-top: 1px solid var(--border-color);">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="saveOrder()">
                            <i class="bi bi-save me-2"></i>保存
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    const existingModal = document.getElementById('orderModal');
    if (existingModal) existingModal.remove();
    
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    const modal = new bootstrap.Modal(document.getElementById('orderModal'));
    modal.show();
    
    document.getElementById('orderModal').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

function saveOrder() {
    const form = document.getElementById('orderForm');
    if (!form) return;
    
    const formData = new FormData(form);
    const orderId = formData.get('order_id');
    
    const orderData = {
        order_number: formData.get('order_number'),
        total_amount: parseFloat(formData.get('total_amount')) || 0,
        user_id: formData.get('user_id') ? parseInt(formData.get('user_id')) : null,
        payment_status: formData.get('payment_status'),
        order_status: formData.get('order_status')
    };
    
    const url = orderId ? 
        `${window.CONTEXT_PATH}/AdminServlet?action=update&table=orders&id=${orderId}` :
        `${window.CONTEXT_PATH}/AdminServlet?action=create&table=orders`;
    
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            bootstrap.Modal.getInstance(document.getElementById('orderModal')).hide();
            loadOrders();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('注文を保存しました', 'success');
            } else {
                alert('注文が正常に保存されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error saving order:', error);
    });
}

function deleteOrder(orderId) {
    if (!confirm('この注文を削除してもよろしいですか？')) return;
    
    fetch(`${window.CONTEXT_PATH}/AdminServlet?action=delete&table=orders&id=${orderId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadOrders();
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('注文を削除しました', 'success');
            } else {
                alert('注文が削除されました。');
            }
        } else {
        }
    })
    .catch(error => {
        console.error('Error deleting order:', error);
    });
}

async function loadActivityLogs() {
    try {
        const url = `${window.CONTEXT_PATH}/AdminServlet?action=getActivityLogs&limit=50`;
        const data = await KaruruUtils.apiFetch(url);
        const container = document.getElementById('activityLogs');
        
        if (!container) {
            console.error('Activity logs container not found');
            return;
        }
        
        const logs = KaruruUtils.extractData(data, 'logs') || data.logs || data.data || [];
        
        if (logs && logs.length > 0) {
            container.innerHTML = `
                <div class="table-responsive">
                    <table class="table table-light table-striped table-hover">
                         <thead class="table-light">
                            <tr>
                                 <th class="fw-bold">日時</th>
                                 <th class="fw-bold">ユーザー</th>
                                 <th class="fw-bold">アクション</th>
                                 <th class="fw-bold">詳細</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${logs.map(log => `
                                 <tr>
                                     <td>${log.created_at ? (typeof KaruruUtils !== 'undefined' && KaruruUtils.formatDate
                                        ? KaruruUtils.formatDate(log.created_at) 
                                        : new Date(log.created_at).toLocaleString('ja-JP')) : 'N/A'}</td>
                                     <td>
                                         <i class="bi bi-person me-2 text-info"></i>
                                         ${log.username || log.full_name || 'N/A'}
                                     </td>
                                     <td>
                                         <span class="badge bg-primary">${log.action || 'N/A'}</span>
                                     </td>
                                     <td>${log.details || '-'}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            `;
        } else {
            container.innerHTML = '<p class="text-muted">アクティビティログがありません。</p>';
        }
    } catch (error) {
        console.error('Error loading activity logs:', error);
        const container = document.getElementById('activityLogs');
        if (container) {
            container.innerHTML = '<p class="text-danger">アクティビティログの読み込みに失敗しました。</p>';
        }
    }
}

