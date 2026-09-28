// Home page (index.jsp): banners, categories and three product rows.

document.addEventListener('DOMContentLoaded', () => {
    loadBanners();
    loadCategories();
    loadProducts('getFeatured&limit=6', 'featuredProducts', { hideWhenEmpty: 'featuredSection' });
    loadProducts('getAvailableProducts&limit=12&sort=newest', 'recentProducts');
    loadProducts('getPopular&limit=6', 'popularProducts');
});

/** Banner links are stored relative to the app ("/products.jsp") or as full URLs. */
function bannerHref(link) {
    if (!link) return null;
    if (/^https?:\/\//.test(link) || link.startsWith(window.CONTEXT_PATH + '/')) return link;
    return window.CONTEXT_PATH + (link.startsWith('/') ? link : '/' + link);
}

async function loadBanners() {
    let banners;
    try {
        banners = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/BannerServlet?action=getActive`);
    } catch (error) {
        return; // Banners are decoration; the page works without them.
    }
    if (!banners || !banners.length) return;

    const slider = document.getElementById('bannerSlider');
    slider.querySelector('.carousel-inner').innerHTML = banners.map((banner, index) => {
        const href = bannerHref(banner.link_url);
        const tag = href ? 'a' : 'div';
        return `
            <div class="carousel-item${index === 0 ? ' active' : ''}">
                <${tag} class="banner"${href ? ` href="${escapeHtml(href)}"` : ''}>
                    <img src="${KaruruUtils.resolveImageUrl(banner.image_url)}" alt="${escapeHtml(banner.title)}"
                         onerror="this.remove()">
                    <span class="banner-title">${escapeHtml(banner.title)}</span>
                </${tag}>
            </div>`;
    }).join('');
    slider.querySelectorAll('.carousel-control-prev, .carousel-control-next')
        .forEach(control => { control.hidden = banners.length < 2; });
    document.getElementById('bannerSection').hidden = false;
}

async function loadCategories() {
    const strip = document.getElementById('categoriesGrid');
    try {
        const categories = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        strip.innerHTML = categories.map(KaruruUtils.categoryTile).join('');
    } catch (error) {
        strip.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', 'カテゴリーを読み込めませんでした');
    }
}

async function loadProducts(query, containerId, { hideWhenEmpty } = {}) {
    const grid = document.getElementById(containerId);
    try {
        const data = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/ProductServlet?action=${query}`);
        const products = KaruruUtils.extractData(data, 'products') || [];
        if (hideWhenEmpty) {
            document.getElementById(hideWhenEmpty).hidden = products.length === 0;
        }
        KaruruUtils.renderProductGrid(grid, products);
    } catch (error) {
        grid.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', '商品を読み込めませんでした');
    }
}
