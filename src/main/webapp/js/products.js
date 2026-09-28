// Product search (products.jsp). The filter form is submitted as a normal GET, so the page URL is
// the single source of truth: read it, fill the form, ask SearchServlet, render.

const searchParams = new URLSearchParams(window.location.search);
let nextPage = 1;

document.addEventListener('DOMContentLoaded', async () => {
    const form = document.getElementById('filterForm');
    renderConditionFilters();
    await loadCategoryOptions();
    fillForm(form);

    // Sorting and category changes apply at once; typed values (keyword, prices) wait for submit.
    form.sortFilter.addEventListener('change', () => form.requestSubmit());
    form.categoryFilter.addEventListener('change', () => form.requestSubmit());
    form.searchInput.addEventListener('input', debounce(loadSuggestions, 250));
    document.getElementById('loadMore').addEventListener('click', () => loadResults());

    loadResults();
});

function renderConditionFilters() {
    document.getElementById('conditionFilters').innerHTML = Object.entries(KaruruUtils.CONDITIONS)
        .map(([value, label]) => `
            <div class="form-check">
                <input class="form-check-input" type="checkbox" name="condition" value="${value}" id="condition-${value}">
                <label class="form-check-label" for="condition-${value}">${label}</label>
            </div>`).join('');
}

async function loadCategoryOptions() {
    try {
        const categories = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        document.getElementById('categoryFilter').insertAdjacentHTML('beforeend', categories
            .map(c => `<option value="${c.category_id}">${escapeHtml(c.category_name)}</option>`).join(''));
    } catch (error) {
        // The rest of the search still works without the category list.
    }
}

function fillForm(form) {
    form.searchInput.value = searchParams.get('search') || '';
    form.sortFilter.value = searchParams.get('sort') || 'newest';
    form.categoryFilter.value = searchParams.get('category') || '';
    form.min.value = searchParams.get('min') || '';
    form.max.value = searchParams.get('max') || '';
    form.rentalFilter.checked = searchParams.get('rental') === '1';
    const conditions = searchParams.getAll('condition');
    form.querySelectorAll('input[name="condition"]').forEach(box => {
        box.checked = conditions.includes(box.value);
    });
}

/** SearchServlet parameters for the current page URL. */
function searchQuery(page) {
    const query = { page, sort: searchParams.get('sort') || 'newest' };
    const text = (searchParams.get('search') || '').trim();
    if (text) query.query = text;
    if (searchParams.get('category')) query.categories = searchParams.get('category');
    if (searchParams.getAll('condition').length) query.conditions = searchParams.getAll('condition').join(',');
    if (searchParams.get('rental') === '1') query.rental = '1';

    const min = searchParams.get('min');
    const max = searchParams.get('max');
    if (min || max) query.price = `${min || 0}-${max || '+'}`;
    return query;
}

async function loadResults() {
    const grid = document.getElementById('productsGrid');
    const loadMore = document.getElementById('loadMore');
    const firstPage = nextPage === 1;
    loadMore.disabled = true;
    try {
        const data = await KaruruUtils.apiFetch(
            KaruruUtils.buildUrl(`${window.CONTEXT_PATH}/SearchServlet`, searchQuery(nextPage)));
        if (firstPage) {
            showSummary(data.totalCount);
            showRelatedMatches(data.categories, data.sellers);
            KaruruUtils.renderProductGrid(grid, data.products, '条件に合う商品が見つかりませんでした');
        } else {
            grid.insertAdjacentHTML('beforeend', data.products.map(KaruruUtils.productCard).join(''));
        }
        nextPage++;
        loadMore.hidden = !data.hasMore;
    } catch (error) {
        if (firstPage) grid.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', error.message);
        else KaruruUtils.showNotification(error.message, 'danger');
    } finally {
        loadMore.disabled = false;
    }
}

function showSummary(total) {
    const text = (searchParams.get('search') || '').trim();
    if (text) {
        document.getElementById('resultsTitle').textContent = `「${text}」の検索結果`;
        document.title = `「${text}」の検索結果 | カルル`;
    }
    document.getElementById('resultsInfo').textContent = `${total.toLocaleString('ja-JP')}件`;
}

/** Categories and sellers whose name matches the keyword, as links above the products. */
function showRelatedMatches(categories = [], sellers = []) {
    const box = document.getElementById('relatedMatches');
    const links = [
        ...categories.map(c => `<a class="chip" href="${window.CONTEXT_PATH}/products.jsp?category=${c.category_id}">
            <i class="bi bi-tag"></i>${escapeHtml(c.category_name)}</a>`),
        ...sellers.map(s => `<a class="chip" href="${window.CONTEXT_PATH}/seller.jsp?seller_id=${s.seller_id}">
            <i class="bi bi-person"></i>${escapeHtml(s.username)}</a>`)
    ];
    box.innerHTML = links.join('');
    box.hidden = links.length === 0;
}

async function loadSuggestions(event) {
    const text = event.target.value.trim();
    const list = document.getElementById('searchSuggestions');
    if (text.length < 2) {
        list.innerHTML = '';
        return;
    }
    try {
        const data = await KaruruUtils.apiFetch(
            `${window.CONTEXT_PATH}/SearchServlet?action=suggestions&limit=8&query=${encodeURIComponent(text)}`);
        list.innerHTML = [...new Set(data.suggestions.map(s => s.text))]
            .map(value => `<option value="${escapeHtml(value)}">`).join('');
    } catch (error) {
        list.innerHTML = '';
    }
}

function debounce(fn, wait) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), wait);
    };
}
