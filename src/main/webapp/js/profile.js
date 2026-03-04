// Profile page JavaScript

let currentProfile = null;
let currentAddresses = [];
let editingAddressId = null;

document.addEventListener('DOMContentLoaded', function() {
    loadProfile();
    loadAddresses();
    loadUserStats();
    
    document.getElementById('profileForm').addEventListener('submit', handleProfileUpdate);
    document.getElementById('addAddressBtn').addEventListener('click', showAddAddressModal);
    document.getElementById('saveAddressBtn').addEventListener('click', saveAddress);
    
    // Setup avatar upload
    setupAvatarUpload();
    
    // Setup address modal
    setupAddressModal();
    
    // Initialize postal code auto-fill
    initializePostalCodeAutoFill();
});

async function loadProfile() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet?action=getProfile`);
        const data = await response.json();
        
        let profile = null;
        if (data.user) {
            profile = data.user;
        } else if (data.username) {
            profile = data;
        }
        
        if (profile) {
            currentProfile = profile;
            
            // Update form fields
            const usernameEl = document.getElementById('username');
            const emailEl = document.getElementById('email');
            const fullNameEl = document.getElementById('fullName');
            const phoneEl = document.getElementById('phone');
            const bioEl = document.getElementById('bio');
            
            if (usernameEl) usernameEl.value = profile.username || '';
            if (emailEl) emailEl.value = profile.email || '';
            if (fullNameEl) fullNameEl.value = profile.full_name || '';
            if (phoneEl) phoneEl.value = profile.phone || '';
            if (bioEl) bioEl.value = profile.bio || '';
            
            // Update display
            const displayName = document.getElementById('displayName');
            const displayUsername = document.getElementById('displayUsername');
            
            if (displayName) {
                displayName.textContent = profile.full_name || profile.username || 'ユーザー';
            }
            if (displayUsername) {
                displayUsername.textContent = `@${profile.username || 'username'}`;
            }
            
            // Update avatar
            const avatarImg = document.getElementById('avatarImg');
            if (avatarImg) {
                const fullAvatarUrl = KaruruUtils.resolveAvatarUrl(profile.avatar_url);
                avatarImg.src = fullAvatarUrl;
                avatarImg.onerror = function() {
                    this.src = KaruruUtils.resolveAvatarUrl('');
                };
            }
            
            // Update products count if available
            const productsCountEl = document.getElementById('productsCount');
            if (productsCountEl && profile.products_count !== undefined) {
                productsCountEl.textContent = profile.products_count || 0;
            }
        }
    } catch (error) {
        console.error('Error loading profile:', error);
    }
}

async function loadUserStats() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet?action=getUserStats`);
        const data = await response.json();
        
        if (data && data.success !== false) {
            const stats = data.stats || data;
            
            const productsCountEl = document.getElementById('productsCount');
            const ordersCountEl = document.getElementById('ordersCount');
            const reviewsCountEl = document.getElementById('reviewsCount');
            const ratingEl = document.getElementById('rating');
            
            if (productsCountEl) {
                productsCountEl.textContent = stats.total_products || stats.products_count || 0;
            }
            if (ordersCountEl) {
                ordersCountEl.textContent = stats.total_orders || 0;
            }
            if (reviewsCountEl) {
                reviewsCountEl.textContent = stats.total_reviews || 0;
            }
            if (ratingEl) {
                const rating = stats.average_rating || stats.rating || 0;
                ratingEl.textContent = rating > 0 ? rating.toFixed(1) : '-';
            }
        }
    } catch (error) {
        console.error('Error loading user stats:', error);
    }
}

async function loadAddresses() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet?action=getAddresses`);
        let addresses = [];
        
        const data = await response.json();
        if (Array.isArray(data)) {
            addresses = data;
        } else if (data.addresses) {
            addresses = data.addresses;
        } else if (data.success && data.data) {
            addresses = Array.isArray(data.data) ? data.data : [];
        }
        
        currentAddresses = addresses;
        renderAddresses(addresses);
        
    } catch (error) {
        console.error('Error loading addresses:', error);
        const container = document.getElementById('addressesList');
        if (container) {
            container.innerHTML = `
                <div class="alert alert-danger">
                    <i class="bi bi-exclamation-triangle me-2"></i>住所の読み込みに失敗しました
                </div>
            `;
        }
    }
}

function renderAddresses(addresses) {
    const container = document.getElementById('addressesList');
    if (!container) return;
    
    if (!addresses || addresses.length === 0) {
        container.innerHTML = `
            <div class="text-center py-4">
                <i class="bi bi-geo-alt" style="font-size: 3rem; color: var(--text-muted); opacity: 0.5;"></i>
                <p class="text-muted mt-3 mb-4">登録されている住所がありません</p>
                <button class="btn btn-primary" onclick="showAddAddressModal()">
                    <i class="bi bi-plus-circle me-2"></i>最初の住所を追加
                </button>
            </div>
        `;
        return;
    }
    
    container.innerHTML = addresses.map(address => {
        const isDefault = address.is_default === true || address.is_default === 1;
        
        return `
            <div class="address-card ${isDefault ? 'default' : ''}">
                <div class="d-flex justify-content-between align-items-start mb-3">
                    <div class="flex-grow-1">
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <h6 class="mb-0">
                                <i class="bi bi-geo-alt me-2 text-primary"></i>${escapeHtml(address.address_label || '住所')}
                            </h6>
                            ${isDefault ? '<span class="address-badge bg-primary">デフォルト</span>' : ''}
                        </div>
                        <div class="text-muted small">
                            <div class="mb-1">
                                <i class="bi bi-person me-1"></i><strong>${escapeHtml(address.recipient_name || '')}</strong>
                            </div>
                            <div class="mb-1">
                                <i class="bi bi-telephone me-1"></i>${escapeHtml(address.phone || '')}
                            </div>
                            <div class="mb-1">
                                <i class="bi bi-mailbox me-1"></i>〒${escapeHtml(address.postal_code || '')}
                            </div>
                            <div>
                                <i class="bi bi-geo me-1"></i>${escapeHtml(address.prefecture || '')} ${escapeHtml(address.city || '')}<br>
                                &nbsp;&nbsp;&nbsp;${escapeHtml(address.address_line1 || '')}
                                ${address.address_line2 ? '<br>&nbsp;&nbsp;&nbsp;' + escapeHtml(address.address_line2) : ''}
                            </div>
                        </div>
                    </div>
                </div>
                <div class="d-flex gap-2">
                    <button class="btn btn-sm btn-outline-primary" onclick="editAddress(${address.address_id})">
                        <i class="bi bi-pencil me-1"></i>編集
                    </button>
                    ${!isDefault ? `
                        <button class="btn btn-sm btn-outline-info" onclick="setDefaultAddress(${address.address_id})">
                            <i class="bi bi-star me-1"></i>デフォルトに設定
                        </button>
                    ` : ''}
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteAddress(${address.address_id})">
                        <i class="bi bi-trash me-1"></i>削除
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

function setupAvatarUpload() {
    const avatarInput = document.getElementById('avatarInput');
    const changeAvatarBtn = document.getElementById('changeAvatarBtn');
    const avatarWrapper = document.getElementById('avatarWrapper');
    const avatarImg = document.getElementById('avatarImg');
    const avatarPreview = document.getElementById('avatarPreview');
    const avatarUploadStatus = document.getElementById('avatarUploadStatus');
    
    if (!avatarInput || !changeAvatarBtn || !avatarWrapper) return;
    
    // Click avatar or button to trigger file input
    avatarWrapper.addEventListener('click', function() {
        avatarInput.click();
    });
    
    changeAvatarBtn.addEventListener('click', function(e) {
        e.stopPropagation();
        avatarInput.click();
    });
    
    // Handle file selection
    avatarInput.addEventListener('change', function(e) {
        const file = e.target.files[0];
        if (!file) return;
        
        // Validate file type
        if (!file.type.startsWith('image/')) {
            return;
        }
        
        // Validate file size (10MB)
        if (file.size > 10 * 1024 * 1024) {
            return;
        }
        
        // Show preview
        const reader = new FileReader();
        reader.onload = function(e) {
            if (avatarPreview) {
                avatarPreview.style.backgroundImage = `url(${e.target.result})`;
                avatarPreview.style.display = 'block';
            }
        };
        reader.readAsDataURL(file);
        
        // Upload avatar
        uploadAvatar(file);
    });
}

async function uploadAvatar(file) {
    const avatarUploadStatus = document.getElementById('avatarUploadStatus');
    const changeAvatarBtn = document.getElementById('changeAvatarBtn');
    const avatarImg = document.getElementById('avatarImg');
    const avatarPreview = document.getElementById('avatarPreview');
    
    try {
        // Show loading
        if (avatarUploadStatus) {
            avatarUploadStatus.innerHTML = '<span class="text-info"><i class="bi bi-hourglass-split me-1"></i>アップロード中...</span>';
        }
        if (changeAvatarBtn) {
            changeAvatarBtn.disabled = true;
        }
        
        const formData = new FormData();
        formData.append('action', 'uploadAvatar');
        formData.append('avatar', file);
        
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet`, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (result.success) {
            // Update avatar image
            if (result.avatar_url && avatarImg) {
                const fullAvatarUrl = KaruruUtils.resolveAvatarUrl(result.avatar_url);
                avatarImg.src = fullAvatarUrl;
                if (avatarPreview) {
                    avatarPreview.style.display = 'none';
                }
            }
            
            if (avatarUploadStatus) {
                avatarUploadStatus.innerHTML = '<span class="text-success"><i class="bi bi-check-circle me-1"></i>アバターを更新しました</span>';
                setTimeout(() => {
                    avatarUploadStatus.innerHTML = '';
                }, 3000);
            }
            
        } else {
            throw new Error(result.message || result.error || 'アバターのアップロードに失敗しました');
        }
    } catch (error) {
        console.error('Error uploading avatar:', error);
        if (avatarUploadStatus) {
            avatarUploadStatus.innerHTML = '<span class="text-danger"><i class="bi bi-x-circle me-1"></i>アップロードに失敗しました</span>';
        }
    } finally {
        if (changeAvatarBtn) {
            changeAvatarBtn.disabled = false;
        }
    }
}

async function handleProfileUpdate(e) {
    e.preventDefault();
    
    const saveBtn = document.getElementById('saveProfileBtn');
    const originalText = saveBtn ? saveBtn.innerHTML : '';
    
    try {
        // Show loading
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>保存中...';
        }
        
        const formData = new FormData(e.target);
        formData.append('action', 'updateProfile');
        
        // Remove avatar from form data (handled separately)
        formData.delete('avatar');
        
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet`, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (result.success) {
            // Reload profile to show updated data
            setTimeout(() => {
                loadProfile();
                loadUserStats();
            }, 500);
        } else {
            throw new Error(result.message || result.error || '更新に失敗しました');
        }
    } catch (error) {
        console.error('Error updating profile:', error);
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalText;
        }
    }
}

function resetProfileForm() {
    if (currentProfile) {
        document.getElementById('email').value = currentProfile.email || '';
        document.getElementById('fullName').value = currentProfile.full_name || '';
        document.getElementById('phone').value = currentProfile.phone || '';
        document.getElementById('bio').value = currentProfile.bio || '';
    }
}

function setupAddressModal() {
    const modal = document.getElementById('addressModal');
    if (!modal) return;
    
    modal.addEventListener('hidden.bs.modal', function() {
        document.getElementById('addressForm').reset();
        document.getElementById('addressId').value = '';
        editingAddressId = null;
        document.getElementById('addressModalLabel').innerHTML = '<i class="bi bi-geo-alt me-2"></i>住所を追加';
    });
}

function showAddAddressModal() {
    editingAddressId = null;
    document.getElementById('addressForm').reset();
    document.getElementById('addressId').value = '';
    document.getElementById('addressModalLabel').innerHTML = '<i class="bi bi-geo-alt me-2"></i>住所を追加';
    
    const modal = new bootstrap.Modal(document.getElementById('addressModal'));
    modal.show();
}

function editAddress(addressId) {
    const address = currentAddresses.find(addr => addr.address_id === addressId);
    if (!address) {
        return;
    }
    
    editingAddressId = addressId;
    document.getElementById('addressId').value = addressId;
    document.getElementById('addressLabel').value = address.address_label || '';
    document.getElementById('recipientName').value = address.recipient_name || '';
    document.getElementById('addressPhone').value = address.phone || '';
    document.getElementById('postalCode').value = address.postal_code || '';
    document.getElementById('prefecture').value = address.prefecture || '';
    document.getElementById('city').value = address.city || '';
    document.getElementById('addressLine1').value = address.address_line1 || '';
    document.getElementById('addressLine2').value = address.address_line2 || '';
    document.getElementById('isDefault').checked = address.is_default === true || address.is_default === 1;
    
    document.getElementById('addressModalLabel').innerHTML = '<i class="bi bi-geo-alt me-2"></i>住所を編集';
    
    const modal = new bootstrap.Modal(document.getElementById('addressModal'));
    modal.show();
}

async function saveAddress() {
    const form = document.getElementById('addressForm');
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }
    
    const saveBtn = document.getElementById('saveAddressBtn');
    const originalText = saveBtn ? saveBtn.innerHTML : '';
    
    try {
        // Show loading
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>保存中...';
        }
        
        const formData = new FormData(form);
        const addressId = document.getElementById('addressId').value;
        const action = addressId ? 'updateAddress' : 'addAddress';
        
        formData.append('action', action);
        if (addressId) {
            formData.append('address_id', addressId);
        }
        if (document.getElementById('isDefault').checked) {
            formData.append('is_default', 'true');
        }
        
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet`, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        if (result.success) {
            
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('addressModal'));
            if (modal) {
                modal.hide();
            }
            
            // Reload addresses
            loadAddresses();
        } else {
            throw new Error(result.message || result.error || '保存に失敗しました');
        }
    } catch (error) {
        console.error('Error saving address:', error);
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = originalText;
        }
    }
}

async function setDefaultAddress(addressId) {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'setDefaultAddress',
                address_id: addressId.toString()
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        if (result.success !== false) {
            loadAddresses();
        } else {
            throw new Error(result.message || result.error || '設定に失敗しました');
        }
    } catch (error) {
        console.error('Error setting default address:', error);
    }
}

async function deleteAddress(addressId) {
    if (!confirm('この住所を削除しますか？')) {
        return;
    }
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/ProfileServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: new URLSearchParams({
                action: 'deleteAddress',
                address_id: addressId.toString()
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const result = await response.json();
        if (result.success !== false) {
            loadAddresses();
        } else {
            throw new Error(result.message || result.error || '削除に失敗しました');
        }
    } catch (error) {
        console.error('Error deleting address:', error);
    }
}

// Initialize postal code auto-fill functionality
function initializePostalCodeAutoFill() {
    const postalCodeInput = document.getElementById('postalCode');
    if (!postalCodeInput) return;
    
    let debounceTimer;
    
    postalCodeInput.addEventListener('input', function() {
        const postalCode = this.value.replace(/\s|-/g, ''); // Remove spaces and dashes
        
        // Clear previous timer
        clearTimeout(debounceTimer);
        
        // Only fetch if postal code is 7 digits
        if (postalCode.length === 7 && /^\d{7}$/.test(postalCode)) {
            debounceTimer = setTimeout(() => {
                fetchAddressFromPostalCode(postalCode);
            }, 500); // Wait 500ms after user stops typing
        } else if (postalCode.length === 0) {
            // Clear fields if postal code is empty
            clearAddressFields();
        }
    });
    
    // Also handle paste event
    postalCodeInput.addEventListener('paste', function() {
        setTimeout(() => {
            const postalCode = this.value.replace(/\s|-/g, '');
            if (postalCode.length === 7 && /^\d{7}$/.test(postalCode)) {
                fetchAddressFromPostalCode(postalCode);
            }
        }, 100);
    });
}

// Fetch address information from Japan Postal Code API
async function fetchAddressFromPostalCode(postalCode) {
    if (!postalCode || postalCode.length !== 7) return;
    
    const postalCodeInput = document.getElementById('postalCode');
    const postalCodeStatus = document.getElementById('postalCodeStatus');
    const spinner = postalCodeStatus?.querySelector('.spinner-border');
    const checkIcon = postalCodeStatus?.querySelector('.bi-check-circle');
    
    try {
        // Show loading indicator
        if (postalCodeStatus) {
            postalCodeStatus.style.display = 'flex';
            if (spinner) spinner.style.display = 'inline-block';
            if (checkIcon) checkIcon.style.display = 'none';
        }
        
        // Use zipcloud.ibsnet.co.jp API (free and simple)
        const response = await fetch(`https://zipcloud.ibsnet.co.jp/api/search?zipcode=${postalCode}`);
        const data = await response.json();
        
        if (data.status === 200 && data.results && data.results.length > 0) {
            const result = data.results[0];
            
            // Format postal code with dash (123-4567)
            const formattedPostalCode = postalCode.substring(0, 3) + '-' + postalCode.substring(3);
            
            // Fill form fields
            document.getElementById('postalCode').value = formattedPostalCode;
            document.getElementById('prefecture').value = result.prefcode ? getPrefectureName(result.prefcode) : result.address1 || '';
            document.getElementById('city').value = result.address2 || '';
            document.getElementById('addressLine1').value = result.address3 || '';
            
            // Show success indicator
            if (postalCodeStatus) {
                if (spinner) spinner.style.display = 'none';
                if (checkIcon) checkIcon.style.display = 'inline-block';
                setTimeout(() => {
                    postalCodeStatus.style.display = 'none';
                }, 2000);
            }
            
        } else {
            // Postal code not found
            if (postalCodeStatus) {
                postalCodeStatus.style.display = 'none';
            }
            if (data.message) {
                if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                    KaruruUtils.showNotification(data.message, 'warning');
                }
            } else {
                if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
                    KaruruUtils.showNotification('郵便番号が見つかりませんでした', 'warning');
                }
            }
        }
    } catch (error) {
        console.error('Error fetching address from postal code:', error);
        if (postalCodeStatus) {
            postalCodeStatus.style.display = 'none';
        }
    }
}

// Get prefecture name from prefecture code
function getPrefectureName(prefCode) {
    const prefectures = {
        '01': '北海道', '02': '青森県', '03': '岩手県', '04': '宮城県', '05': '秋田県',
        '06': '山形県', '07': '福島県', '08': '茨城県', '09': '栃木県', '10': '群馬県',
        '11': '埼玉県', '12': '千葉県', '13': '東京都', '14': '神奈川県', '15': '新潟県',
        '16': '富山県', '17': '石川県', '18': '福井県', '19': '山梨県', '20': '長野県',
        '21': '岐阜県', '22': '静岡県', '23': '愛知県', '24': '三重県', '25': '滋賀県',
        '26': '京都府', '27': '大阪府', '28': '兵庫県', '29': '奈良県', '30': '和歌山県',
        '31': '鳥取県', '32': '島根県', '33': '岡山県', '34': '広島県', '35': '山口県',
        '36': '徳島県', '37': '香川県', '38': '愛媛県', '39': '高知県', '40': '福岡県',
        '41': '佐賀県', '42': '長崎県', '43': '熊本県', '44': '大分県', '45': '宮崎県',
        '46': '鹿児島県', '47': '沖縄県'
    };
    
    const code = String(prefCode).padStart(2, '0');
    return prefectures[code] || '';
}

// Clear address fields
function clearAddressFields() {
    const prefectureField = document.getElementById('prefecture');
    const cityField = document.getElementById('city');
    const addressLine1Field = document.getElementById('addressLine1');
    
    if (prefectureField) prefectureField.value = '';
    if (cityField) cityField.value = '';
    if (addressLine1Field) addressLine1Field.value = '';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
