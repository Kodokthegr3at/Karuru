// Admin Banner Management JavaScript

let banners = [];
let bannerModal = null;

document.addEventListener('DOMContentLoaded', function() {
    bannerModal = new bootstrap.Modal(document.getElementById('bannerModal'));
    loadBanners();
});

async function loadBanners() {
    try {
        const url = `${window.CONTEXT_PATH}/BannerServlet?action=getAll`;
        console.log('Loading banners from:', url);
        
        const data = await KaruruUtils.apiFetch(url);
        console.log('Banner data received:', data);
        
        // Handle different response formats
        if (Array.isArray(data)) {
            banners = data;
        } else if (data && data.banners) {
            banners = data.banners;
        } else if (data && data.success && data.banners) {
            banners = data.banners;
        } else {
            banners = KaruruUtils.extractData(data, 'banners') || [];
        }
        
        console.log('Processed banners:', banners);
        renderBanners();
    } catch (error) {
        console.error('Error loading banners:', error);
        console.error('Error details:', error.message, error.stack);
        
        const tbody = document.getElementById('bannersTableBody');
        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="9" class="text-center py-5 text-danger">
                        <i class="bi bi-exclamation-triangle" style="font-size: 2rem;"></i>
                        <p class="mt-3 mb-0">バナーの読み込みに失敗しました</p>
                        <small class="text-muted d-block mt-2">${error.message || ''}</small>
                        <button class="btn btn-primary mt-3" onclick="loadBanners()">再読み込み</button>
                    </td>
                </tr>
            `;
        }
        
    }
}

function renderBanners() {
    const tbody = document.getElementById('bannersTableBody');
    if (!tbody) return;
    
    if (banners.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" class="text-center py-5 text-muted">
                    <i class="bi bi-inbox" style="font-size: 3rem; opacity: 0.3;"></i>
                    <p class="mt-3 mb-0">バナーがありません</p>
                </td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = banners.map(banner => {
        const startDate = banner.start_date ? new Date(banner.start_date).toLocaleString('ja-JP') : '-';
        const endDate = banner.end_date ? new Date(banner.end_date).toLocaleString('ja-JP') : '-';
        const imageUrl = banner.image_url ? 
            (banner.image_url.startsWith('/') ? `${window.CONTEXT_PATH}${banner.image_url}` : 
             banner.image_url.startsWith('http') ? banner.image_url : 
             `${window.CONTEXT_PATH}/${banner.image_url}`) : '';
        
        return `
            <tr>
                <td>${banner.banner_id}</td>
                <td>${escapeHtml(banner.title || '')}</td>
                <td>
                    ${imageUrl ? `
                        <img src="${imageUrl}" alt="${escapeHtml(banner.title || '')}" 
                             style="width: 100px; height: 50px; object-fit: cover; border-radius: 4px;"
                             onerror="this.src='${window.CONTEXT_PATH}/img/default-product.png'">
                    ` : '-'}
                </td>
                <td>
                    <span class="badge bg-info">${getPositionLabel(banner.position)}</span>
                </td>
                <td>${banner.display_order || 0}</td>
                <td>
                    ${banner.is_active ? 
                        '<span class="badge bg-success">アクティブ</span>' : 
                        '<span class="badge bg-secondary">非アクティブ</span>'}
                </td>
                <td><small>${startDate}</small></td>
                <td><small>${endDate}</small></td>
                <td>
                    <button class="btn btn-sm btn-primary me-1" onclick="openBannerModal(${banner.banner_id})" title="編集">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-danger" onclick="deleteBanner(${banner.banner_id})" title="削除">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function getPositionLabel(position) {
    const labels = {
        'home_top': 'ホーム上部',
        'home_middle': 'ホーム中央',
        'category': 'カテゴリー',
        'product': '商品'
    };
    return labels[position] || position;
}

async function openBannerModal(bannerId) {
    const form = document.getElementById('bannerForm');
    const modalLabel = document.getElementById('bannerModalLabel');
    
    form.reset();
    document.getElementById('bannerId').value = '';
    document.getElementById('bannerIsActive').checked = true;
    
    if (bannerId) {
        modalLabel.textContent = 'バナー編集';
        
        try {
            const url = `${window.CONTEXT_PATH}/BannerServlet?action=getById&banner_id=${bannerId}`;
            const data = await KaruruUtils.apiFetch(url);
            const banner = KaruruUtils.extractData(data, 'banner') || data.banner;
            
            if (banner) {
                document.getElementById('bannerId').value = banner.banner_id;
                document.getElementById('bannerTitle').value = banner.title || '';
                document.getElementById('bannerImageUrl').value = banner.image_url || '';
                document.getElementById('bannerLinkUrl').value = banner.link_url || '';
                document.getElementById('bannerPosition').value = banner.position || 'home_top';
                document.getElementById('bannerDisplayOrder').value = banner.display_order || 0;
                document.getElementById('bannerIsActive').checked = banner.is_active !== false;
                
                if (banner.start_date) {
                    const startDate = new Date(banner.start_date);
                    document.getElementById('bannerStartDate').value = formatDateTimeLocal(startDate);
                }
                
                if (banner.end_date) {
                    const endDate = new Date(banner.end_date);
                    document.getElementById('bannerEndDate').value = formatDateTimeLocal(endDate);
                }
            }
        } catch (error) {
            console.error('Error loading banner:', error);
            return;
        }
    } else {
        modalLabel.textContent = '新規バナー';
    }
    
    bannerModal.show();
}

function formatDateTimeLocal(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
}

async function saveBanner() {
    const form = document.getElementById('bannerForm');
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }
    
    const formData = new FormData(form);
    const bannerId = formData.get('banner_id');
    const action = bannerId ? 'update' : 'create';
    
    const params = new URLSearchParams();
    params.append('action', action);
    
    if (bannerId) {
        params.append('banner_id', bannerId);
    }
    
    params.append('title', formData.get('title'));
    params.append('image_url', formData.get('image_url'));
    params.append('link_url', formData.get('link_url') || '');
    params.append('position', formData.get('position'));
    params.append('display_order', formData.get('display_order') || '0');
    params.append('is_active', document.getElementById('bannerIsActive').checked ? '1' : '0');
    
    const startDate = formData.get('start_date');
    if (startDate) {
        params.append('start_date', startDate.replace('T', ' '));
    }
    
    const endDate = formData.get('end_date');
    if (endDate) {
        params.append('end_date', endDate.replace('T', ' '));
    }
    
    try {
        const url = `${window.CONTEXT_PATH}/BannerServlet`;
        console.log('Saving banner:', { action, bannerId, params: params.toString() });
        
        const data = await KaruruUtils.apiFetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString()
        });
        
        console.log('Save banner response:', data);
        
        if (data && data.success !== false) {
            bannerModal.hide();
            loadBanners();
        } else {
            const errorMsg = data?.error || data?.message || 'エラーが発生しました';
            console.error('Save banner error:', errorMsg);
        }
    } catch (error) {
        console.error('Error saving banner:', error);
    }
}

async function deleteBanner(bannerId) {
    if (!confirm('このバナーを削除してもよろしいですか？')) {
        return;
    }
    
    try {
        const params = new URLSearchParams();
        params.append('action', 'delete');
        params.append('banner_id', bannerId);
        
        const url = `${window.CONTEXT_PATH}/BannerServlet`;
        console.log('Deleting banner:', { bannerId, params: params.toString() });
        
        const data = await KaruruUtils.apiFetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: params.toString()
        });
        
        console.log('Delete banner response:', data);
        
        if (data && data.success !== false) {
            loadBanners();
        } else {
            const errorMsg = data?.error || data?.message || 'エラーが発生しました';
            console.error('Delete banner error:', errorMsg);
        }
    } catch (error) {
        console.error('Error deleting banner:', error);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

