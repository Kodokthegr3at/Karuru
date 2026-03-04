// Dashboard page JavaScript

let salesChart = null;

function initDashboard() {
    initializeNavigation();
    loadOverview();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initDashboard);
} else {
    initDashboard();
}

function initializeNavigation() {
    const navItems = document.querySelectorAll('.dashboard-nav-link[data-section]');
    const sections = document.querySelectorAll('.dashboard-section');
    
    document.body.addEventListener('click', function(e) {
        const trigger = e.target.closest('[data-section-trigger]');
        if (trigger) {
            e.preventDefault();
            const section = trigger.getAttribute('data-section-trigger');
            const navItem = document.querySelector(`.dashboard-nav-link[data-section="${section}"]`);
            if (navItem) navItem.click();
        }
    });
    
    navItems.forEach(item => {
        item.addEventListener('click', function(e) {
            e.preventDefault();
            const targetSection = this.getAttribute('data-section');
            
            // Update active nav item
            navItems.forEach(nav => {
                nav.classList.remove('active');
            });
            this.classList.add('active');
            
            // Show target section first, then load data
            sections.forEach(section => {
                section.classList.add('d-none');
                if (section.id === targetSection) {
                    section.classList.remove('d-none');
                    // Small delay to ensure section is visible before loading
                    setTimeout(() => {
                        loadSection(targetSection);
                    }, 50);
                }
            });
        });
    });
}

function loadSection(sectionId) {
    switch(sectionId) {
        case 'overview':
            loadOverview();
            break;
        case 'products':
            loadUserProducts();
            break;
        case 'orders':
            loadUserOrders();
            break;
        case 'sales':
            loadSales();
            break;
        case 'messages':
            loadDashboardMessages();
            break;
    }
}

async function loadOverview() {
    try {
        const url = `${window.CONTEXT_PATH}/DashboardServlet?action=getStats`;
        console.log('Fetching dashboard stats from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        const stats = KaruruUtils.extractData(data) || data;
        
        console.log('Dashboard stats data received:', stats);
        
        const activeListingsEl = document.getElementById('activeListings');
        const totalSalesEl = document.getElementById('totalSales');
        const totalOrdersEl = document.getElementById('totalOrders');
        const unreadMessagesEl = document.getElementById('unreadMessages');
        
        if (activeListingsEl) {
            activeListingsEl.textContent = stats.activeListings != null ? stats.activeListings : 0;
        }
        if (totalSalesEl) {
            const sales = stats.totalSales != null ? parseFloat(stats.totalSales) : 0;
            totalSalesEl.textContent = formatPrice(sales);
            
            // Update tooltip with monthly comparison if available
            if (stats.salesThisMonth != null && stats.salesLastMonth != null) {
                const thisMonth = parseFloat(stats.salesThisMonth);
                const lastMonth = parseFloat(stats.salesLastMonth);
                let changePercent = 0;
                if (lastMonth > 0) {
                    changePercent = ((thisMonth - lastMonth) / lastMonth) * 100;
                } else if (thisMonth > 0) {
                    changePercent = 100;
                }
                const changeEl = totalSalesEl.parentElement.querySelector('.stat-card-change');
                if (changeEl) {
                    if (changePercent > 0) {
                        changeEl.innerHTML = `<i class="bi bi-arrow-up"></i> <span class="text-success">+${changePercent.toFixed(1)}%</span> <span class="text-muted">今月</span>`;
                    } else if (changePercent < 0) {
                        changeEl.innerHTML = `<i class="bi bi-arrow-down"></i> <span class="text-danger">${changePercent.toFixed(1)}%</span> <span class="text-muted">今月</span>`;
                    } else {
                        changeEl.innerHTML = `<i class="bi bi-dash"></i> <span class="text-muted">今月</span>`;
                    }
                }
            }
        }
        if (totalOrdersEl) {
            totalOrdersEl.textContent = stats.totalOrders != null ? stats.totalOrders : 0;
            // Update with pending orders info if available
            if (stats.pendingOrders != null && parseInt(stats.pendingOrders) > 0) {
                const changeEl = totalOrdersEl.parentElement.querySelector('.stat-card-change');
                if (changeEl) {
                    changeEl.innerHTML = `<i class="bi bi-hourglass-split"></i> <span class="text-warning">${stats.pendingOrders}件処理中</span>`;
                }
            }
        }
        if (unreadMessagesEl) {
            unreadMessagesEl.textContent = stats.unreadMessages != null ? stats.unreadMessages : 0;
        }
        
        // Load additional overview content
        loadOverviewContent();
    } catch (error) {
        console.error('Error loading overview:', error);
        // Set default values on error
        const activeListingsEl = document.getElementById('activeListings');
        const totalSalesEl = document.getElementById('totalSales');
        const totalOrdersEl = document.getElementById('totalOrders');
        const unreadMessagesEl = document.getElementById('unreadMessages');
        
        if (activeListingsEl) activeListingsEl.textContent = '0';
        if (totalSalesEl) totalSalesEl.textContent = '\u00A50';
        if (totalOrdersEl) totalOrdersEl.textContent = '0';
        if (unreadMessagesEl) unreadMessagesEl.textContent = '0';
    }
}

async function loadOverviewContent() {
    const overviewSection = document.querySelector('#overview .card-body');
    if (!overviewSection) return;
    
    try {
        // Load recent activity and stats
        const [stats, recentOrders, recentProducts] = await Promise.all([
            loadQuickStats(),
            loadRecentOrders(5),
            loadRecentProducts(5)
        ]);
        
        overviewSection.innerHTML = `
            <div class="row g-3 mb-3">
                <div class="col-md-6">
                    <div class="card card-light">
                        <div class="card-body">
                            <h6 class="mb-3">
                                <i class="bi bi-lightning-charge-fill me-2 text-warning"></i>クイックアクション
                            </h6>
                            <div class="d-grid gap-2">
                                <a href="${window.CONTEXT_PATH}/create-listing.jsp" class="btn btn-primary btn-sm">
                                    <i class="bi bi-plus-circle me-1"></i>新しい商品を出品
                                </a>
                                <a href="${window.CONTEXT_PATH}/orders.jsp" class="btn btn-outline-primary btn-sm">
                                    <i class="bi bi-receipt me-1"></i>注文履歴を見る
                                </a>
                                <a href="${window.CONTEXT_PATH}/messages.jsp" class="btn btn-outline-primary btn-sm">
                                    <i class="bi bi-chat-dots me-1"></i>メッセージを確認
                                </a>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="card card-light">
                        <div class="card-body">
                            <h6 class="mb-3">
                                <i class="bi bi-info-circle-fill me-2 text-info"></i>ダッシュボードについて
                            </h6>
                            <p class="text-muted small mb-0">
                                このダッシュボードでは、出品商品、注文、売上、メッセージなどの情報を確認できます。
                                左側のメニューから各セクションにアクセスできます。
                            </p>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="row g-3">
                <div class="col-md-6">
                    <div class="card card-light">
                        <div class="card-header card-header-light">
                            <h6 class="mb-0">
                                <i class="bi bi-receipt me-2 text-primary"></i>最近の注文
                            </h6>
                        </div>
                        <div class="card-body">
                            ${recentOrders.length > 0 ? `
                                <div class="list-group list-group-flush">
                                    ${recentOrders.map(order => `
                                        <div class="list-group-item px-0 py-2 dashboard-list-item" style="background: transparent; border-color: var(--border-color);">
                                            <div class="d-flex justify-content-between align-items-start gap-2">
                                                <div class="flex-grow-1 min-w-0">
                                                    <small class="fw-bold d-block text-truncate">${escapeForTemplateLiteral(escapeHtml(order.order_number || '#' + order.order_id))}</small>
                                                    <small class="text-muted">${formatPrice(order.total_amount || 0)}</small>
                                                </div>
                                                <div class="text-end flex-shrink-0">
                                                    <span class="badge bg-${getOrderStatusBadge(order.order_status)} mb-1">${getOrderStatusText(order.order_status)}</span><br>
                                                    <span class="text-muted recent-date">${formatDate(order.created_at)}</span>
                                                </div>
                                            </div>
                                        </div>
                                    `).join('')}
                                </div>
                                <div class="mt-2">
                                    <a href="${window.CONTEXT_PATH}/orders.jsp" class="btn btn-sm btn-outline-primary w-100">
                                        すべての注文を見る
                                    </a>
                                </div>
                            ` : `
                                <p class="text-muted small mb-0 text-center">最近の注文がありません</p>
                            `}
                        </div>
                    </div>
                </div>
                
                <div class="col-md-6">
                    <div class="card card-light">
                        <div class="card-header card-header-light">
                            <h6 class="mb-0">
                                <i class="bi bi-box-seam me-2 text-primary"></i>最近の商品
                            </h6>
                        </div>
                        <div class="card-body">
                            ${recentProducts.length > 0 ? `
                                <div class="list-group list-group-flush">
                                    ${recentProducts.map(product => `
                                        <div class="list-group-item px-0 py-2 dashboard-list-item" style="background: transparent; border-color: var(--border-color);">
                                            <div class="d-flex justify-content-between align-items-start gap-2 flex-wrap">
                                                <div class="flex-grow-1" style="flex: 1 1 65%;">
                                                    <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}" 
                                                       class="text-decoration-none recent-product-name">
                                                        ${escapeForTemplateLiteral(escapeHtml(product.product_name || '商品名なし'))}
                                                    </a><br>
                                                    <small class="text-primary">${formatPrice(product.price || 0)}</small>
                                                </div>
                                                <div class="text-end flex-shrink-0">
                                                    <span class="badge bg-${getProductStatusBadge(product.status)} mb-1">${getProductStatus(product.status)}</span><br>
                                                    <span class="text-muted recent-date">${formatDate(product.created_at)}</span>
                                                </div>
                                            </div>
                                        </div>
                                    `).join('')}
                                </div>
                                <div class="mt-2">
                                    <a href="#products" class="btn btn-sm btn-outline-primary w-100" data-section-trigger="products">
                                        すべての商品を見る
                                    </a>
                                </div>
                            ` : `
                                <p class="text-muted small mb-0 text-center">最近の商品がありません</p>
                            `}
                        </div>
                    </div>
                </div>
            </div>
        `;
    } catch (error) {
        console.error('Error loading overview content:', error);
    }
}

async function loadQuickStats() {
    // This can be expanded to load additional quick stats
    return {};
}

async function loadRecentOrders(limit = 5) {
    try {
        const url = `${window.CONTEXT_PATH}/OrderServlet?action=getUserOrders&limit=${limit}`;
        const data = await KaruruUtils.apiFetch(url);
        const orders = KaruruUtils.extractData(data, 'orders') || 
                      (Array.isArray(data) ? data : []);
        return orders.slice(0, limit);
    } catch (error) {
        console.error('Error loading recent orders:', error);
        return [];
    }
}

async function loadRecentProducts(limit = 5) {
    try {
        const url = `${window.CONTEXT_PATH}/ProductServlet?action=getUserProducts&limit=${limit}`;
        const data = await KaruruUtils.apiFetch(url);
        const products = KaruruUtils.extractData(data, 'products') || 
                        (Array.isArray(data) ? data : []);
        return products.slice(0, limit);
    } catch (error) {
        console.error('Error loading recent products:', error);
        return [];
    }
}

let allUserProducts = []; // Store all products for filtering

async function loadUserProducts() {
    try {
        const url = `${window.CONTEXT_PATH}/ProductServlet?action=getUserProducts`;
        console.log('Fetching user products from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        const products = KaruruUtils.extractData(data, 'products') || 
                        (Array.isArray(data) ? data : []);
        
        console.log('User products data received:', products);
        
        // Store all products for filtering
        allUserProducts = products || [];
        
        // Display products
        displayProducts(allUserProducts);
    } catch (error) {
        console.error('Error loading user products:', error);
        const container = document.getElementById('userProducts');
        if (container) {
            container.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">商品の読み込みに失敗しました</p>
                        <button onclick="loadUserProducts()" class="btn btn-primary mt-3">
                            <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                        </button>
                    </div>
                </div>
            `;
        }
    }
}

function displayProducts(products) {
    const container = document.getElementById('userProducts');
    if (!container) return;
    
    if (products && products.length > 0) {
        container.innerHTML = products.map(product => {
                const images = Array.isArray(product.images) ? product.images.filter(img => img && img.trim()) : [];
                const imageUrl = product.image_url || (images.length > 0 ? images[0] : '/img/default-product.png');
                const fullImageUrl = imageUrl.startsWith('/') ? window.CONTEXT_PATH + imageUrl : 
                                    (imageUrl.startsWith('http') ? imageUrl : window.CONTEXT_PATH + '/' + imageUrl);
                
                return `
                    <div class="col-6 col-md-6 col-lg-4">
                        <div class="card card-light h-100">
                            <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}" class="text-decoration-none">
                                <img src="${fullImageUrl}" 
                                     class="card-img-top" 
                                     alt="${escapeForTemplateLiteral(escapeHtml(product.product_name || 'Product'))}"
                                     style="height: 200px; object-fit: cover; cursor: pointer;"
                                     onerror="this.onerror=null; this.src='${escapeForJsString(KaruruUtils.resolveProductImageUrl('') || '')}';">
                            </a>
                            <div class="card-body">
                                <h6 class="card-title">
                                    <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}"
                                       class="text-decoration-none">
                                        ${escapeForTemplateLiteral(escapeHtml(product.product_name || '商品名なし'))}
                                    </a>
                                </h6>
                                <p class="card-text text-primary fw-bold mb-2">${formatPrice(product.price || 0)}</p>
                                <p class="card-text mb-2">
                                    <span class="badge bg-${getProductStatusBadge(product.status)}">${getProductStatus(product.status)}</span>
                                    ${product.views_count > 0 ? `<small class="text-muted ms-2"><i class="bi bi-eye me-1"></i>${product.views_count}</small>` : ''}
                                    ${product.likes_count > 0 ? `<small class="text-muted ms-2"><i class="bi bi-heart me-1"></i>${product.likes_count}</small>` : ''}
                                </p>
                            </div>
                            <div class="card-footer" style="background: transparent; border-top: 1px solid var(--border-color);">
                                <div class="btn-group w-100" role="group">
                                    <a href="${window.CONTEXT_PATH}/product-detail.jsp?id=${product.product_id}" 
                                       class="btn btn-sm btn-outline-primary">
                                        <i class="bi bi-eye me-1"></i>表示
                                    </a>
                                    <button onclick="editProduct(${product.product_id})" 
                                            class="btn btn-sm btn-outline-secondary">
                                        <i class="bi bi-pencil me-1"></i>編集
                                    </button>
                                    <button onclick="deleteProduct(${product.product_id}, '${escapeForJsString(escapeForTemplateLiteral(product.product_name || 'この商品'))}')" 
                                            class="btn btn-sm btn-outline-danger">
                                        <i class="bi bi-trash me-1"></i>削除
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        } else {
            container.innerHTML = `
                <div class="col-12">
                    <div class="text-center py-5 text-muted">
                        <i class="bi bi-box-seam" style="font-size: 4rem; opacity: 0.5;"></i>
                        <p class="mt-3 mb-0">出品中の商品がありません</p>
                        <a href="${window.CONTEXT_PATH}/create-listing.jsp" class="btn btn-primary mt-3">
                            <i class="bi bi-plus-circle me-1"></i>新しい商品を出品
                        </a>
                    </div>
                </div>
            `;
        }
}

function filterProducts() {
    const searchInput = document.getElementById('productSearchInput');
    const statusFilter = document.getElementById('productStatusFilter');
    const sortFilter = document.getElementById('productSortFilter');
    
    const searchTerm = searchInput ? searchInput.value.toLowerCase().trim() : '';
    const statusValue = statusFilter ? statusFilter.value : '';
    
    let filtered = [...allUserProducts];
    
    // Filter by search term
    if (searchTerm) {
        filtered = filtered.filter(product => 
            (product.product_name || '').toLowerCase().includes(searchTerm) ||
            (product.description || '').toLowerCase().includes(searchTerm)
        );
    }
    
    // Filter by status
    if (statusValue) {
        filtered = filtered.filter(product => product.status === statusValue);
    }
    
    // Sort products
    const sortValue = sortFilter ? sortFilter.value : 'newest';
    filtered.sort((a, b) => {
        switch(sortValue) {
            case 'oldest':
                return new Date(a.created_at || 0) - new Date(b.created_at || 0);
            case 'price_high':
                return (parseFloat(b.price || 0)) - (parseFloat(a.price || 0));
            case 'price_low':
                return (parseFloat(a.price || 0)) - (parseFloat(b.price || 0));
            case 'views':
                return (parseInt(b.views_count || 0)) - (parseInt(a.views_count || 0));
            case 'newest':
            default:
                return new Date(b.created_at || 0) - new Date(a.created_at || 0);
        }
    });
    
    // Display filtered products
    displayProducts(filtered);
}

async function loadUserOrders() {
    try {
        const url = `${window.CONTEXT_PATH}/OrderServlet?action=getUserOrders`;
        console.log('Fetching user orders from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        const orders = KaruruUtils.extractData(data, 'orders') || 
                      (Array.isArray(data) ? data : []);
        
        console.log('User orders data received:', orders);
        console.log('First order sample:', orders.length > 0 ? orders[0] : 'No orders');
        
        // Log each order's fields to debug
        if (orders.length > 0) {
            orders.forEach((order, index) => {
                console.log(`Order ${index + 1}:`, {
                    order_id: order.order_id,
                    order_number: order.order_number,
                    created_at: order.created_at,
                    total_amount: order.total_amount,
                    order_status: order.order_status,
                    payment_status: order.payment_status
                });
            });
        }
        
        const container = document.getElementById('userOrders');
        if (!container) return;
        
        if (orders && orders.length > 0) {
            container.innerHTML = `
                <div class="dashboard-orders-table d-none d-lg-block">
                    <div class="table-responsive">
                        <table class="table table-light table-hover mb-0">
                            <thead>
                                <tr>
                                    <th>注文番号</th>
                                    <th>合計</th>
                                    <th>状態</th>
                                    <th>支払い状態</th>
                                    <th>注文日</th>
                                    <th>操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${orders.map(order => {
                                    const orderNumber = order.order_number || (order.order_id ? `#${order.order_id}` : '-');
                                    const orderDate = order.created_at ? formatDate(order.created_at) : (order.order_date ? formatDate(order.order_date) : '-');
                                    return `
                                    <tr>
                                        <td>${escapeForTemplateLiteral(escapeHtml(orderNumber))}</td>
                                        <td class="fw-bold text-primary">${formatPrice(order.total_amount || 0)}</td>
                                        <td><span class="badge bg-${getOrderStatusBadge(order.order_status)}">${getOrderStatusText(order.order_status)}</span></td>
                                        <td><span class="badge bg-${getPaymentStatusBadge(order.payment_status)}">${getPaymentStatusText(order.payment_status)}</span></td>
                                        <td class="order-date-cell">${orderDate}</td>
                                        <td>
                                            <div class="d-flex gap-1">
                                                <a href="${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}" 
                                                   class="btn btn-sm btn-outline-primary">
                                                    <i class="bi bi-eye me-1"></i>詳細
                                                </a>
                                                ${getOrderActionButtons(order)}
                                            </div>
                                        </td>
                                    </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
                <div class="dashboard-orders-cards d-lg-none">
                    ${orders.map(order => {
                        const orderNumber = order.order_number || (order.order_id ? `#${order.order_id}` : '-');
                        const orderDate = order.created_at ? formatDate(order.created_at) : (order.order_date ? formatDate(order.order_date) : '-');
                        return `
                        <div class="card card-light mb-2 dashboard-order-card">
                            <div class="card-body py-2 px-3">
                                <div class="d-flex justify-content-between align-items-start gap-2">
                                    <div class="flex-grow-1 min-w-0">
                                        <span class="fw-bold d-block" style="font-size: 0.85rem;">${escapeForTemplateLiteral(escapeHtml(orderNumber))}</span>
                                        <span class="text-primary fw-bold" style="font-size: 0.95rem;">${formatPrice(order.total_amount || 0)}</span>
                                    </div>
                                    <div class="text-end flex-shrink-0">
                                        <span class="badge bg-${getOrderStatusBadge(order.order_status)} mb-1">${getOrderStatusText(order.order_status)}</span>
                                        <span class="badge bg-${getPaymentStatusBadge(order.payment_status)} d-block">${getPaymentStatusText(order.payment_status)}</span>
                                        <span class="text-muted d-block mt-1" style="font-size: 0.75rem;">${orderDate}</span>
                                    </div>
                                </div>
                                <div class="mt-2 pt-2 border-top border-light d-flex gap-1 flex-wrap">
                                    <a href="${window.CONTEXT_PATH}/order-detail.jsp?id=${order.order_id}" 
                                       class="btn btn-sm btn-success flex-grow-1">
                                        <i class="bi bi-eye me-1"></i>詳細
                                    </a>
                                    ${getOrderActionButtons(order) || ''}
                                </div>
                            </div>
                        </div>
                        `;
                    }).join('')}
                </div>
                <div class="mt-3 text-end">
                    <a href="${window.CONTEXT_PATH}/orders.jsp" class="btn btn-outline-primary btn-sm">
                        <i class="bi bi-arrow-right me-1"></i>すべての注文を見る
                    </a>
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-receipt" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">注文履歴がありません</p>
                    <a href="${window.CONTEXT_PATH}/products.jsp" class="btn btn-primary mt-3">
                        <i class="bi bi-cart me-1"></i>商品を見る
                    </a>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading user orders:', error);
        const container = document.getElementById('userOrders');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">注文の読み込みに失敗しました</p>
                    <button onclick="loadUserOrders()" class="btn btn-primary mt-3">
                        <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                    </button>
                </div>
            `;
        }
    }
}

async function loadSales() {
    try {
        // Ensure the sales section is visible before loading chart
        const salesSection = document.getElementById('sales');
        if (salesSection && salesSection.classList.contains('d-none')) {
            salesSection.classList.remove('d-none');
        }
        
        const url = `${window.CONTEXT_PATH}/DashboardServlet?action=getSales`;
        console.log('Fetching sales data from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        const salesData = KaruruUtils.extractData(data, 'sales') || [];
        
        console.log('Sales data received:', salesData);
        
        // Update monthly totals
        const now = new Date();
        const currentMonth = now.getMonth();
        const currentYear = now.getFullYear();
        
        let monthlySales = 0;
        let monthlyOrders = 0;
        
        if (salesData && salesData.length > 0) {
            salesData.forEach(sale => {
                try {
                    const saleDate = new Date(sale.date || sale.sale_date);
                    if (!isNaN(saleDate.getTime()) && 
                        saleDate.getMonth() === currentMonth && 
                        saleDate.getFullYear() === currentYear) {
                        monthlySales += parseFloat(sale.revenue || sale.total || 0);
                        monthlyOrders += parseInt(sale.orderCount || sale.order_count || 0);
                    }
                } catch (e) {
                    console.warn('Error processing sale data:', e, sale);
                }
            });
        }
        
        const monthlySalesEl = document.getElementById('monthlySales');
        const monthlyOrdersEl = document.getElementById('monthlyOrders');
        
        if (monthlySalesEl) {
            monthlySalesEl.textContent = formatPrice(monthlySales);
        }
        if (monthlyOrdersEl) {
            monthlyOrdersEl.textContent = monthlyOrders;
        }
        
        // Destroy existing chart if it exists
        if (salesChart) {
            salesChart.destroy();
            salesChart = null;
        }
        
        // Wait a bit to ensure DOM is ready and Chart.js is loaded
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // Create new chart
        const ctx = document.getElementById('salesChart');
        if (!ctx) {
            console.error('Sales chart canvas not found');
            return;
        }
        
        if (typeof Chart === 'undefined') {
            console.error('Chart.js is not loaded');
            const container = ctx.parentElement;
            if (container) {
                container.innerHTML = `
                    <div class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                        <p class="mt-3 mb-0">チャートライブラリの読み込みに失敗しました</p>
                    </div>
                `;
            }
            return;
        }
        
        if (salesData && salesData.length > 0) {
            // Prepare chart data - sort by date ascending for proper display
            const sortedData = [...salesData].sort((a, b) => {
                const dateA = new Date(a.date || a.sale_date);
                const dateB = new Date(b.date || b.sale_date);
                return dateA - dateB;
            });
            
            const labels = sortedData.slice(-30).map(sale => {
                try {
                    const date = new Date(sale.date || sale.sale_date);
                    if (!isNaN(date.getTime())) {
                        return `${date.getMonth() + 1}/${date.getDate()}`;
                    }
                } catch (e) {
                    // Ignore
                }
                return '';
            }).filter(label => label !== '');
            
            const revenues = sortedData.slice(-30).map(sale => {
                return parseFloat(sale.revenue || sale.total || 0);
            });
            const orderCounts = sortedData.slice(-30).map(sale => {
                return parseInt(sale.orderCount || sale.order_count || 0);
            });
            
            salesChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: '売上 (\u00A5)',
                        data: revenues,
                        borderColor: 'rgb(13, 110, 253)',
                        backgroundColor: 'rgba(13, 110, 253, 0.1)',
                        tension: 0.4,
                        fill: true,
                        yAxisID: 'y'
                    }, {
                        label: '注文数',
                        data: orderCounts,
                        borderColor: 'rgb(25, 135, 84)',
                        backgroundColor: 'rgba(25, 135, 84, 0.1)',
                        tension: 0.4,
                        fill: true,
                        yAxisID: 'y1',
                        type: 'bar'
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: true,
                    interaction: {
                        mode: 'index',
                        intersect: false,
                    },
                        plugins: {
                            legend: {
                                display: true,
                                position: 'top',
                                labels: {
                                    color: '#333333'
                                }
                            },
                        tooltip: {
                            backgroundColor: 'rgba(0, 0, 0, 0.8)',
                            titleColor: '#ffffff',
                            bodyColor: '#ffffff',
                            borderColor: 'rgba(255, 255, 255, 0.1)',
                            borderWidth: 1
                        }
                    },
                    scales: {
                        x: {
                            ticks: {
                                color: '#6c757d'
                            },
                            grid: {
                                color: '#E0E0E0'
                            }
                        },
                        y: {
                            type: 'linear',
                            display: true,
                            position: 'left',
                            ticks: {
                                color: '#6c757d',
                                callback: function(value) {
                                    return '\u00A5' + value.toLocaleString('ja-JP');
                                }
                            },
                            grid: {
                                color: '#E0E0E0'
                            }
                        },
                        y1: {
                            type: 'linear',
                            display: true,
                            position: 'right',
                            ticks: {
                                color: '#6c757d'
                            },
                            grid: {
                                drawOnChartArea: false
                            }
                        }
                    }
                }
            });
        } else {
            // No data to display
            const container = ctx.parentElement;
            if (container) {
                container.innerHTML = `
                    <div class="text-center py-5 text-muted">
                        <i class="bi bi-graph-up" style="font-size: 4rem; opacity: 0.5;"></i>
                        <p class="mt-3 mb-0">売上データがありません</p>
                    </div>
                `;
            }
        }
    } catch (error) {
        console.error('Error loading sales:', error);
        const container = document.getElementById('salesChart')?.parentElement;
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">売上データの読み込みに失敗しました</p>
                    <small class="text-muted d-block mt-2">${error.message || ''}</small>
                </div>
            `;
        }
    }
}

async function loadDashboardMessages() {
    try {
        const url = `${window.CONTEXT_PATH}/MessagesServlet?action=getConversations`;
        console.log('Fetching dashboard messages from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        const conversations = KaruruUtils.extractData(data, 'conversations') || 
                            (Array.isArray(data) ? data : []);
        
        console.log('Dashboard messages data received:', conversations);
        
        const container = document.getElementById('dashboardMessages');
        if (!container) return;
        
        if (conversations && conversations.length > 0) {
            container.innerHTML = conversations.map(conv => `
                <div class="card card-light mb-3">
                    <div class="card-body">
                        <div class="d-flex justify-content-between align-items-start">
                            <div>
                                <h6 class="mb-1">${escapeForTemplateLiteral(escapeHtml(conv.other_user_name || 'ユーザー'))}</h6>
                                <p class="text-muted mb-0 small">${escapeForTemplateLiteral(escapeHtml(conv.last_message || 'メッセージなし'))}</p>
                            </div>
                            ${conv.unread_count > 0 ? `
                                <span class="badge bg-primary">${conv.unread_count}</span>
                            ` : ''}
                        </div>
                        <a href="${window.CONTEXT_PATH}/messages.jsp?user_id=${conv.other_user_id}" 
                           class="btn btn-sm btn-outline-primary mt-2">
                            <i class="bi bi-chat-dots me-1"></i>メッセージを見る
                        </a>
                    </div>
                </div>
            `).join('');
        } else {
            container.innerHTML = `
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-chat-dots" style="font-size: 4rem; opacity: 0.5;"></i>
                    <p class="mt-3 mb-0">メッセージがありません</p>
                    <a href="${window.CONTEXT_PATH}/messages.jsp" class="btn btn-primary mt-3">
                        <i class="bi bi-chat-dots me-1"></i>メッセージを開始
                    </a>
                </div>
            `;
        }
    } catch (error) {
        console.error('Error loading messages:', error);
        const container = document.getElementById('dashboardMessages');
        if (container) {
            container.innerHTML = `
                <div class="text-center py-5 text-danger">
                    <i class="bi bi-exclamation-triangle" style="font-size: 3rem;"></i>
                    <p class="mt-3 mb-0">メッセージの読み込みに失敗しました</p>
                    <button onclick="loadDashboardMessages()" class="btn btn-primary mt-3">
                        <i class="bi bi-arrow-clockwise me-1"></i>再読み込み
                    </button>
                </div>
            `;
        }
    }
}

function getOrderStatusText(status) {
    const statuses = {
        'pending': '保留中',
        'confirmed': '確定済み',
        'processing': '処理中',
        'shipped': '発送済み',
        'delivered': '配送済み',
        'cancelled': 'キャンセル'
    };
    return statuses[status] || status;
}

function getOrderStatusBadge(status) {
    const badges = {
        'pending': 'warning',
        'confirmed': 'info',
        'processing': 'primary',
        'shipped': 'info',
        'delivered': 'success',
        'cancelled': 'danger'
    };
    return badges[status] || 'secondary';
}

function getProductStatus(status) {
    const statuses = {
        'available': '販売中',
        'sold': '売却済み',
        'reserved': '予約中',
        'draft': '下書き'
    };
    return statuses[status] || status;
}

function getProductStatusBadge(status) {
    const badges = {
        'available': 'success',
        'sold': 'secondary',
        'reserved': 'warning',
        'draft': 'info'
    };
    return badges[status] || 'secondary';
}

function getPaymentStatusText(status) {
    const statuses = {
        'pending': '未払い',
        'paid': '支払済み',
        'refunded': '返金済み',
        'failed': '失敗'
    };
    return statuses[status] || status;
}

function getPaymentStatusBadge(status) {
    const badges = {
        'pending': 'warning',
        'paid': 'success',
        'refunded': 'info',
        'failed': 'danger'
    };
    return badges[status] || 'secondary';
}

function getOrderActionButtons(order) {
    const currentUserId = parseInt(window.currentUserId || sessionStorage.getItem('userId') || '0');
    const buyerId = parseInt(order.user_id || 0);
    const items = order.items || [];
    const sellerIds = [...new Set(items.map(item => parseInt(item.seller_id) || 0).filter(id => id > 0))];
    const isBuyer = (buyerId === currentUserId);
    const isSeller = sellerIds.includes(currentUserId);
    
    let buttons = '';
    
    // Buyer actions (don't show Pay for cancelled orders)
    if (isBuyer && order.order_status !== 'cancelled' && order.order_status === 'pending' && order.payment_status === 'pending') {
        buttons += `
            <button onclick="processPaymentFromDashboard(${order.order_id})" class="btn btn-sm btn-success" title="支払う">
                <i class="bi bi-credit-card"></i>
            </button>
        `;
    }
    
    // Seller actions (only when user is seller of items in this order)
    if (isSeller && order.payment_status === 'paid' && order.order_status === 'pending') {
        buttons += `
            <button onclick="confirmOrderFromDashboard(${order.order_id})" class="btn btn-sm btn-info" title="注文を確定">
                <i class="bi bi-check-circle"></i>
            </button>
        `;
    }
    
    if (isSeller && order.order_status === 'confirmed') {
        buttons += `
            <button onclick="updateOrderStatusFromDashboard(${order.order_id}, 'processing')" class="btn btn-sm btn-primary" title="処理を開始">
                <i class="bi bi-gear"></i>
            </button>
        `;
    }
    
    if (isSeller && order.order_status === 'processing') {
        buttons += `
            <button onclick="showShippingModalFromDashboard(${order.order_id})" class="btn btn-sm btn-info" title="発送する">
                <i class="bi bi-truck"></i>
            </button>
        `;
    }
    
    if (isSeller && order.order_status === 'shipped') {
        buttons += `
            <button onclick="updateOrderStatusFromDashboard(${order.order_id}, 'delivered')" class="btn btn-sm btn-success" title="配送完了">
                <i class="bi bi-check-circle"></i>
            </button>
        `;
    }
    
    return buttons;
}

async function processPaymentFromDashboard(orderId) {
    try {
        const paymentMethod = prompt('支払い方法を選択してください:\n1. wallet (ウォレット)\n2. credit_card (クレジットカード)\n3. bank_transfer (銀行振込)\n4. cod (代金引換)', 'wallet');
        
        if (!paymentMethod) return;
        
        KaruruUtils.showNotification('支払いを処理しています...', 'info');
        
        const response = await fetch(`${window.CONTEXT_PATH}/Payment`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'processPayment',
                order_id: orderId.toString(),
                payment_method: paymentMethod
            })
        });
        
        const result = await response.json();
        if (result.success !== false) {
            const paymentStatus = result.payment_status || 'pending';
            if (paymentStatus === 'paid') {
                KaruruUtils.showNotification('支払いが完了しました', 'success');
            } else {
                KaruruUtils.showNotification('支払い処理が完了しました（確認待ち）', 'info');
            }
            loadUserOrders();
        } else {
            KaruruUtils.showNotification(result.error || result.message || '支払い処理に失敗しました', 'danger');
        }
    } catch (error) {
        console.error('Error processing payment:', error);
        KaruruUtils.showNotification('支払い処理中にエラーが発生しました', 'danger');
    }
}

async function confirmOrderFromDashboard(orderId) {
    if (!KaruruUtils.confirmDialog('この注文を確定しますか？確定後、在庫が減算されます。')) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('注文を確定しています...', 'info');
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'confirmOrder',
                order_id: orderId.toString()
            })
        });
        
        const result = await response.json();
        if (result.success !== false) {
            loadUserOrders();
        } else {
        }
    } catch (error) {
        console.error('Error confirming order:', error);
    }
}

async function updateOrderStatusFromDashboard(orderId, newStatus) {
    const statusMessages = {
        'processing': '処理を開始しますか？',
        'shipped': '発送済みに更新しますか？',
        'delivered': '配送完了を確認しますか？'
    };
    
    if (newStatus === 'shipped') {
        showShippingModalFromDashboard(orderId);
        return;
    }
    
    if (!KaruruUtils.confirmDialog(statusMessages[newStatus] || 'ステータスを更新しますか？')) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('ステータスを更新しています...', 'info');
        
        const params = new URLSearchParams({
            action: 'updateOrderStatus',
            order_id: orderId.toString(),
            status: newStatus
        });
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params
        });
        
        const result = await response.json();
        if (result.success !== false) {
            const statusTexts = {
                'processing': '処理を開始しました',
                'shipped': '発送済みに更新しました',
                'delivered': '配送完了を確認しました'
            };
            loadUserOrders();
        } else {
        }
    } catch (error) {
        console.error('Error updating order status:', error);
    }
}

function showShippingModalFromDashboard(orderId) {
    // Create and show shipping modal
    const modalHtml = `
        <div class="modal fade" id="shippingModalDashboard" tabindex="-1">
            <div class="modal-dialog">
                <div class="modal-content card-light">
                    <div class="modal-header card-header-light">
                        <h5 class="modal-title">発送情報を入力</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <label for="trackingNumberDashboard" class="form-label">追跡番号 <span class="text-danger">*</span></label>
                            <input type="text" class="form-control form-control-light" id="trackingNumberDashboard" 
                                   placeholder="追跡番号を入力" required>
                        </div>
                        <div class="mb-3">
                            <label for="courierDashboard" class="form-label">配送業者 <span class="text-danger">*</span></label>
                            <select class="form-select form-control-light" id="courierDashboard" required>
                                <option value="">選択してください</option>
                                <option value="ヤマト運輸">ヤマト運輸</option>
                                <option value="佐川急便">佐川急便</option>
                                <option value="日本郵便">日本郵便</option>
                                <option value="その他">その他</option>
                            </select>
                        </div>
                    </div>
                    <div class="modal-footer card-header-light">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">キャンセル</button>
                        <button type="button" class="btn btn-primary" onclick="submitShippingFromDashboard(${orderId})">
                            <i class="bi bi-check-circle me-2"></i>発送する
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    // Remove existing modal if any
    const existingModal = document.getElementById('shippingModalDashboard');
    if (existingModal) {
        existingModal.remove();
    }
    
    // Add modal to body
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    
    // Show modal
    const modal = new bootstrap.Modal(document.getElementById('shippingModalDashboard'));
    modal.show();
    
    // Remove modal from DOM when hidden
    document.getElementById('shippingModalDashboard').addEventListener('hidden.bs.modal', function() {
        this.remove();
    });
}

async function submitShippingFromDashboard(orderId) {
    const trackingNumber = document.getElementById('trackingNumberDashboard')?.value;
    const courier = document.getElementById('courierDashboard')?.value;
    
    if (!trackingNumber || !courier) {
        return;
    }
    
    try {
        KaruruUtils.showNotification('発送情報を更新しています...', 'info');
        
        const params = new URLSearchParams({
            action: 'updateOrderStatus',
            order_id: orderId.toString(),
            status: 'shipped',
            tracking_number: trackingNumber,
            courier: courier
        });
        
        const response = await fetch(`${window.CONTEXT_PATH}/OrderServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params
        });
        
        const result = await response.json();
        if (result.success !== false) {
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('shippingModalDashboard'));
            if (modal) modal.hide();
            
            loadUserOrders();
        } else {
        }
    } catch (error) {
        console.error('Error submitting shipping:', error);
    }
}

function formatPrice(amount) {
    return KaruruUtils.formatPrice(amount);
}

function formatDate(dateString) {
    if (!dateString) return '-';
    try {
        return KaruruUtils.formatDate(dateString);
    } catch (error) {
        console.error('Error formatting date:', error, dateString);
        // Try to format manually if KaruruUtils.formatDate fails
        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return '-';
            return date.toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit'
            });
        } catch (e) {
            return '-';
        }
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function escapeForJsString(text) {
    if (!text) return '';
    return String(text).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function escapeForTemplateLiteral(text) {
    if (!text) return '';
    return String(text).replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${');
}

async function editProduct(productId) {
    // Redirect to product detail page with edit mode or create edit page
    window.location.href = `${window.CONTEXT_PATH}/product-detail.jsp?id=${productId}&edit=true`;
}

async function deleteProduct(productId, productName) {
    if (!confirm(`本当に「${productName}」を削除しますか？\nこの操作は取り消せません。`)) {
        return;
    }
    
    try {
        const url = `${window.CONTEXT_PATH}/ProductServlet?action=delete&id=${productId}`;
        const response = await fetch(url, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        const data = await response.json();
        
        if (data.success || response.ok) {
            if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                KaruruUtils.showNotification('商品を削除しました', 'success');
            }
            // Reload products
            loadUserProducts();
            // Also reload stats if on overview
            const overviewSection = document.getElementById('overview');
            if (overviewSection && !overviewSection.classList.contains('d-none')) {
                loadOverview();
            }
        } else {
        }
    } catch (error) {
        console.error('Error deleting product:', error);
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification(error.message || '削除できませんでした', 'danger');
        }
    }
}
