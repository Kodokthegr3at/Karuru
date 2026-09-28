// Category index (categories.jsp).

document.addEventListener('DOMContentLoaded', async () => {
    const grid = document.getElementById('categoriesGrid');
    try {
        const categories = await KaruruUtils.apiFetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        grid.innerHTML = categories.length
            ? categories.map(KaruruUtils.categoryTile).join('')
            : KaruruUtils.emptyState('bi-tags', 'カテゴリーがありません');
    } catch (error) {
        grid.innerHTML = KaruruUtils.emptyState('bi-exclamation-circle', 'カテゴリーを読み込めませんでした');
    }
});
