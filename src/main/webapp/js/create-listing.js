// Create Listing JavaScript

let selectedImages = [];
let imagePreviewCount = 0;

document.addEventListener('DOMContentLoaded', function() {
    loadCategories();
    setupImageUpload();
    setupRentalToggle();
    setupFormValidation();
    loadDraft(); // Load draft on page load
    
    const form = document.getElementById('createListingForm');
    if (form) {
        form.addEventListener('submit', handleSubmit);
    }
    
    // Auto-save draft every 30 seconds
    setInterval(() => {
        const productName = document.getElementById('productName')?.value || '';
        if (productName.trim().length > 0) {
            saveDraft();
        }
    }, 30000);
    
    // Save draft before page unload
    window.addEventListener('beforeunload', function() {
        saveDraft();
    });
});

async function loadCategories() {
    try {
        // Try CategoryServlet first, fallback to ProductServlet if not available
        let response = await fetch(`${window.CONTEXT_PATH}/CategoryServlet?action=getCategories`);
        if (!response.ok && response.status === 404) {
            console.log('CategoryServlet not found, trying ProductServlet as fallback...');
            response = await fetch(`${window.CONTEXT_PATH}/ProductServlet?action=getCategories`);
        }
        if (!response.ok) {
            throw new Error('Failed to load categories');
        }
        
        let categories = [];
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            categories = Array.isArray(data) ? data : (data.categories || data.data || []);
        } else {
            const text = await response.text();
            categories = JSON.parse(text);
            if (!Array.isArray(categories)) {
                categories = categories.categories || categories.data || [];
            }
        }
        
        const select = document.getElementById('category');
        if (select && categories && categories.length > 0) {
            // Clear existing options except the first one
            while (select.children.length > 1) {
                select.removeChild(select.lastChild);
            }
            
            categories.forEach(category => {
                const option = document.createElement('option');
                option.value = category.category_id;
                option.textContent = category.category_name || 'カテゴリー';
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading categories:', error);
    }
}

function setupImageUpload() {
    const fileInput = document.getElementById('productImages');
    const uploadArea = document.getElementById('imageUploadArea');
    const previewGrid = document.getElementById('imagePreview');
    
    if (!fileInput || !uploadArea) return;
    
    // Click upload area to trigger file input
    uploadArea.addEventListener('click', function(e) {
        // Don't trigger if clicking on existing images
        if (e.target.closest('.col-md-4')) return;
        fileInput.click();
    });
    
    fileInput.addEventListener('change', function(e) {
        const files = Array.from(e.target.files);
        
        // Validate file count
        if (selectedImages.length + files.length > 10) {
            return;
        }
        
        files.forEach(file => {
            // Validate file size (10MB)
            if (file.size > 10 * 1024 * 1024) {
                return;
            }
            
            // Validate file type
            if (!file.type.startsWith('image/')) {
                return;
            }
            
            selectedImages.push(file);
            addImagePreview(file);
        });
        
        // Reset input
        fileInput.value = '';
    });
    
    // Drag and drop
    uploadArea.addEventListener('dragover', function(e) {
        e.preventDefault();
        uploadArea.classList.add('dragover');
    });
    
    uploadArea.addEventListener('dragleave', function(e) {
        e.preventDefault();
        uploadArea.classList.remove('dragover');
    });
    
    uploadArea.addEventListener('drop', function(e) {
        e.preventDefault();
        uploadArea.classList.remove('dragover');
        
        const files = Array.from(e.dataTransfer.files);
        files.forEach(file => {
            if (file.type.startsWith('image/')) {
                fileInput.files = addFileToInput(fileInput, file);
                fileInput.dispatchEvent(new Event('change'));
            }
        });
    });
}

function addFileToInput(input, file) {
    const dt = new DataTransfer();
    Array.from(input.files).forEach(f => dt.items.add(f));
    dt.items.add(file);
    return dt.files;
}

function addImagePreview(file) {
    const previewGrid = document.getElementById('imagePreview');
    const reader = new FileReader();
    
    reader.onload = function(e) {
        const previewItem = document.createElement('div');
        previewItem.className = 'col-6 col-md-4 col-sm-6 mb-2';
        previewItem.dataset.filename = file.name;
        
        // Use current count of preview items + 1 for index
        const imageIndex = previewGrid.children.length + 1;
        
        previewItem.innerHTML = `
            <div class="position-relative">
                <img src="${e.target.result}" alt="Preview" class="img-thumbnail w-100" style="height: 150px; object-fit: cover; border: 1px solid rgba(255,255,255,0.2);">
                <button type="button" class="btn btn-danger btn-sm position-absolute top-0 end-0 m-1" 
                        onclick="removeImage('${file.name.replace(/'/g, "\\'")}')" title="削除">
                    <i class="bi bi-x"></i>
                </button>
                <span class="badge bg-primary position-absolute bottom-0 start-0 m-1">${imageIndex}</span>
            </div>
        `;
        
        previewGrid.appendChild(previewItem);
        
        // Show upload area again if less than 10 images
        const uploadArea = document.getElementById('imageUploadArea');
        if (uploadArea) {
            if (selectedImages.length >= 10) {
            uploadArea.style.display = 'none';
            } else {
                uploadArea.style.display = 'block';
            }
        }
    };
    
    reader.readAsDataURL(file);
}

function removeImage(filename) {
    selectedImages = selectedImages.filter(file => file.name !== filename);
    
    const previewItem = document.querySelector(`[data-filename="${filename}"]`);
    if (previewItem) {
        previewItem.remove();
    }
    
    const uploadArea = document.getElementById('imageUploadArea');
    const previewGrid = document.getElementById('imagePreview');
    
    // Show upload area if no images or less than 10
    if (selectedImages.length === 0) {
        if (uploadArea) uploadArea.style.display = 'block';
        imagePreviewCount = 0;
    } else {
        // Reorder images
        if (previewGrid) {
        previewGrid.querySelectorAll('.col-md-4').forEach((item, index) => {
            const badge = item.querySelector('.badge');
            if (badge) badge.textContent = index + 1;
        });
        }
        // Show upload area if less than 10 images
        if (uploadArea && selectedImages.length < 10) {
            uploadArea.style.display = 'block';
        }
    }
}

function setupRentalToggle() {
    const rentalCheckbox = document.getElementById('isRental');
    const rentalSettings = document.getElementById('rentalSettings');
    
    // Toggle main rental settings
    rentalCheckbox.addEventListener('change', function() {
        if (this.checked) {
            rentalSettings.style.display = 'block';
        } else {
            rentalSettings.style.display = 'none';
            // Clear all rental price inputs when disabled
            document.getElementById('rentalPriceDaily').value = '';
            document.getElementById('rentalPriceWeekly').value = '';
            document.getElementById('rentalPriceMonthly').value = '';
            document.getElementById('enableRentalDaily').checked = false;
            document.getElementById('enableRentalWeekly').checked = false;
            document.getElementById('enableRentalMonthly').checked = false;
            document.getElementById('rentalDailyFields').style.display = 'none';
            document.getElementById('rentalWeeklyFields').style.display = 'none';
            document.getElementById('rentalMonthlyFields').style.display = 'none';
        }
    });
    
    // Toggle daily rental fields
    const enableRentalDaily = document.getElementById('enableRentalDaily');
    const rentalDailyFields = document.getElementById('rentalDailyFields');
    if (enableRentalDaily && rentalDailyFields) {
        enableRentalDaily.addEventListener('change', function() {
            if (this.checked) {
                rentalDailyFields.style.display = 'block';
                document.getElementById('rentalPriceDaily').focus();
            } else {
                rentalDailyFields.style.display = 'none';
                document.getElementById('rentalPriceDaily').value = '';
            }
        });
    }
    
    // Toggle weekly rental fields
    const enableRentalWeekly = document.getElementById('enableRentalWeekly');
    const rentalWeeklyFields = document.getElementById('rentalWeeklyFields');
    if (enableRentalWeekly && rentalWeeklyFields) {
        enableRentalWeekly.addEventListener('change', function() {
            if (this.checked) {
                rentalWeeklyFields.style.display = 'block';
                document.getElementById('rentalPriceWeekly').focus();
            } else {
                rentalWeeklyFields.style.display = 'none';
                document.getElementById('rentalPriceWeekly').value = '';
            }
        });
    }
    
    // Toggle monthly rental fields
    const enableRentalMonthly = document.getElementById('enableRentalMonthly');
    const rentalMonthlyFields = document.getElementById('rentalMonthlyFields');
    if (enableRentalMonthly && rentalMonthlyFields) {
        enableRentalMonthly.addEventListener('change', function() {
            if (this.checked) {
                rentalMonthlyFields.style.display = 'block';
                document.getElementById('rentalPriceMonthly').focus();
            } else {
                rentalMonthlyFields.style.display = 'none';
                document.getElementById('rentalPriceMonthly').value = '';
            }
        });
    }
}

function setupFormValidation() {
    const priceInput = document.getElementById('price');
    const originalPriceInput = document.getElementById('originalPrice');
    const productNameInput = document.getElementById('productName');
    const descriptionInput = document.getElementById('description');
    
    // Validate price - must be positive
    if (priceInput) {
        priceInput.addEventListener('input', function() {
            const price = parseFloat(this.value) || 0;
            if (price <= 0) {
                this.setCustomValidity('価格は0より大きい値である必要があります');
            } else {
                this.setCustomValidity('');
            }
        });
    }
    
    // Validate original price - must be greater than or equal to price
    if (originalPriceInput) {
        originalPriceInput.addEventListener('input', function() {
            const price = parseFloat(priceInput.value) || 0;
            const original = parseFloat(this.value) || 0;
            
            if (original > 0 && price > 0 && original < price) {
                this.setCustomValidity('元の価格は販売価格以上である必要があります');
            } else if (original > 0 && price > 0 && original > price) {
                const discount = Math.round((1 - price / original) * 100);
                this.setCustomValidity(''); // Valid
            } else {
                this.setCustomValidity('');
            }
        });
    }
    
    // Validate product name length
    if (productNameInput) {
        productNameInput.addEventListener('input', function() {
            if (this.value.length > 100) {
                this.setCustomValidity('商品名は100文字以内で入力してください');
            } else {
                this.setCustomValidity('');
            }
        });
    }
    
    // Validate description length
    if (descriptionInput) {
        descriptionInput.addEventListener('input', function() {
            if (this.value.length > 5000) {
                this.setCustomValidity('商品説明は5000文字以内で入力してください');
            } else {
                this.setCustomValidity('');
            }
        });
    }
}

function addSpecification() {
    const container = document.getElementById('specificationsContainer');
    const specItem = document.createElement('div');
    specItem.className = 'specification-item d-flex gap-2 mb-2';
    specItem.innerHTML = `
        <input type="text" class="form-control bg-dark border-secondary text-light spec-name" 
               placeholder="仕様名（例: ブランド）">
        <input type="text" class="form-control bg-dark border-secondary text-light spec-value" 
               placeholder="値（例: Apple）">
        <button type="button" class="btn btn-outline-danger" onclick="removeSpecification(this)">×</button>
    `;
    container.appendChild(specItem);
}

function removeSpecification(button) {
    button.parentElement.remove();
}

async function handleSubmit(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('formError');
    const successDiv = document.getElementById('formSuccess');
    const submitBtn = document.getElementById('submitBtn');
    const submitText = document.getElementById('submitText');
    const submitLoader = document.getElementById('submitLoader');
    
    // Clear previous messages
    if (errorDiv) {
        errorDiv.classList.add('d-none');
        errorDiv.classList.remove('show');
        errorDiv.textContent = '';
    }
    if (successDiv) {
        successDiv.classList.add('d-none');
        successDiv.classList.remove('show');
        successDiv.textContent = '';
    }
    
    // Validate required fields
    const productName = document.getElementById('productName').value.trim();
    const category = document.getElementById('category').value;
    const description = document.getElementById('description').value.trim();
    const price = document.getElementById('price').value;
    const condition = document.getElementById('condition').value;
    const stockQuantity = document.getElementById('stockQuantity').value;
    
    if (!productName) {
        if (errorDiv) {
            errorDiv.textContent = '商品名を入力してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    if (!category) {
        if (errorDiv) {
            errorDiv.textContent = 'カテゴリーを選択してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    if (!description) {
        if (errorDiv) {
            errorDiv.textContent = '商品説明を入力してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    if (!price || parseFloat(price) <= 0) {
        if (errorDiv) {
            errorDiv.textContent = '有効な価格を入力してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    if (!condition) {
        if (errorDiv) {
            errorDiv.textContent = '商品の状態を選択してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    if (!stockQuantity || parseInt(stockQuantity) < 1) {
        if (errorDiv) {
            errorDiv.textContent = '在庫数を正しく入力してください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    // Validate original price
    const originalPrice = document.getElementById('originalPrice').value;
    if (originalPrice && parseFloat(originalPrice) > 0) {
        if (parseFloat(originalPrice) < parseFloat(price)) {
            if (errorDiv) {
                errorDiv.textContent = '元の価格は販売価格以上である必要があります';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
            }
            return;
        }
    }
    
    // Validate images
    if (selectedImages.length === 0) {
        if (errorDiv) {
            errorDiv.textContent = '少なくとも1枚の画像をアップロードしてください';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    // Validate rental fields before submission
    const isRental = document.getElementById('isRental')?.checked;
    if (isRental) {
        const enableRentalDaily = document.getElementById('enableRentalDaily')?.checked;
        const enableRentalWeekly = document.getElementById('enableRentalWeekly')?.checked;
        const enableRentalMonthly = document.getElementById('enableRentalMonthly')?.checked;
        
        // Check if at least one rental period is selected
        if (!enableRentalDaily && !enableRentalWeekly && !enableRentalMonthly) {
            if (errorDiv) {
                errorDiv.textContent = 'レンタルを有効にする場合、少なくとも1つのレンタル期間（日単位、週単位、または月単位）を選択してください';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
            }
            return;
        }
        
        // Validate prices for selected rental periods
        if (enableRentalDaily) {
            const rentalDaily = document.getElementById('rentalPriceDaily')?.value;
            if (!rentalDaily || parseFloat(rentalDaily) <= 0) {
                if (errorDiv) {
                    errorDiv.textContent = '日単位レンタルの価格を正しく入力してください';
                    errorDiv.classList.remove('d-none');
                    errorDiv.classList.add('show');
                }
                return;
            }
        }
        
        if (enableRentalWeekly) {
            const rentalWeekly = document.getElementById('rentalPriceWeekly')?.value;
            if (!rentalWeekly || parseFloat(rentalWeekly) <= 0) {
                if (errorDiv) {
                    errorDiv.textContent = '週単位レンタルの価格を正しく入力してください';
                    errorDiv.classList.remove('d-none');
                    errorDiv.classList.add('show');
                }
                return;
            }
        }
        
        if (enableRentalMonthly) {
            const rentalMonthly = document.getElementById('rentalPriceMonthly')?.value;
            if (!rentalMonthly || parseFloat(rentalMonthly) <= 0) {
                if (errorDiv) {
                    errorDiv.textContent = '月単位レンタルの価格を正しく入力してください';
                    errorDiv.classList.remove('d-none');
                    errorDiv.classList.add('show');
                }
                return;
            }
        }
    }
    
    // Show loading
    if (submitBtn) submitBtn.disabled = true;
    if (submitText) submitText.style.display = 'none';
    if (submitLoader) {
        submitLoader.classList.remove('d-none');
        submitLoader.style.display = 'inline-block';
    }
    
    // Prepare form data
    const formData = new FormData();
    
    // Action parameter - REQUIRED!
    formData.append('action', 'create');
    
    // Basic fields
    formData.append('product_name', document.getElementById('productName').value);
    formData.append('category_id', document.getElementById('category').value);
    formData.append('description', document.getElementById('description').value);
    formData.append('price', document.getElementById('price').value);
    formData.append('stock_quantity', document.getElementById('stockQuantity').value);
    formData.append('condition', document.getElementById('condition').value);
    
    // Optional fields
    const minOrder = document.getElementById('minOrder').value;
    if (minOrder) formData.append('min_order', minOrder);
    
    // Use originalPrice variable already declared above for validation
    if (originalPrice && originalPrice.trim()) {
        formData.append('original_price', originalPrice);
    }
    
    const weight = document.getElementById('weight').value;
    const weightUnit = document.getElementById('weightUnit')?.value || 'kg';
    if (weight) {
        formData.append('weight', weight);
        formData.append('weight_unit', weightUnit);
    }
    
    const brand = document.querySelector('.spec-name')?.value.trim();
    if (brand) formData.append('brand', brand);
    
    if (document.getElementById('isNegotiable')?.checked) {
        formData.append('is_negotiable', '1');
    }
    
    // Rental fields
    if (isRental) {
        formData.append('is_rental', '1');
        
        // Only send prices if the corresponding checkbox is enabled and has value
        const enableRentalDaily = document.getElementById('enableRentalDaily')?.checked;
        const enableRentalWeekly = document.getElementById('enableRentalWeekly')?.checked;
        const enableRentalMonthly = document.getElementById('enableRentalMonthly')?.checked;
        
        if (enableRentalDaily) {
            const rentalDaily = document.getElementById('rentalPriceDaily')?.value;
            if (rentalDaily && parseFloat(rentalDaily) > 0) {
                formData.append('rental_price_daily', rentalDaily);
            }
        }
        
        if (enableRentalWeekly) {
            const rentalWeekly = document.getElementById('rentalPriceWeekly')?.value;
            if (rentalWeekly && parseFloat(rentalWeekly) > 0) {
                formData.append('rental_price_weekly', rentalWeekly);
            }
        }
        
        if (enableRentalMonthly) {
            const rentalMonthly = document.getElementById('rentalPriceMonthly')?.value;
            if (rentalMonthly && parseFloat(rentalMonthly) > 0) {
                formData.append('rental_price_monthly', rentalMonthly);
            }
        }
    }
    
    // Images - CRITICAL: Append each file with name "images"
    selectedImages.forEach((file) => {
        formData.append('images', file);
    });
    
    // Specifications
    const specItems = document.querySelectorAll('.specification-item');
    specItems.forEach((item, index) => {
        const name = item.querySelector('.spec-name')?.value.trim();
        const value = item.querySelector('.spec-value')?.value.trim();
        if (name && value) {
            formData.append('spec_name_' + index, name);
            formData.append('spec_value_' + index, value);
        }
    });
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/CreateListingServlet`, {
            method: 'POST',
            body: formData
            // Don't set Content-Type header - browser will set it with boundary for multipart
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const contentType = response.headers.get('content-type');
        let result = {};
        
        if (contentType && contentType.includes('application/json')) {
            result = await response.json();
        } else {
            const text = await response.text();
            try {
                result = JSON.parse(text);
            } catch (e) {
                throw new Error('Failed to parse response');
            }
        }
        
        if (result.success !== false) {
            if (successDiv) {
                successDiv.textContent = result.message || '商品の出品が完了しました！';
                successDiv.classList.remove('d-none');
                successDiv.classList.add('show');
            }
            
            // Clear form
            document.getElementById('createListingForm').reset();
            selectedImages = [];
            document.getElementById('imagePreview').innerHTML = '';
            document.getElementById('imageUploadArea').style.display = 'block';
            imagePreviewCount = 0;
            
            // Clear draft from localStorage
            localStorage.removeItem('listingDraft');
            
            // Redirect to product page after 2 seconds
            setTimeout(() => {
                if (result.product_id) {
                    window.location.href = `${window.CONTEXT_PATH}/product-detail.jsp?id=${result.product_id}`;
                } else {
                    window.location.href = `${window.CONTEXT_PATH}/dashboard.jsp`;
                }
            }, 2000);
        } else {
            if (errorDiv) {
                errorDiv.textContent = result.message || result.error || '商品の出品に失敗しました';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
            }
        }
    } catch (error) {
        console.error('Error:', error);
        if (errorDiv) {
            errorDiv.textContent = 'エラーが発生しました: ' + error.message + '。もう一度お試しください。';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
    } finally {
        if (submitBtn) submitBtn.disabled = false;
        if (submitText) submitText.style.display = 'inline';
        if (submitLoader) {
            submitLoader.classList.add('d-none');
            submitLoader.style.display = 'none';
        }
    }
}

function saveDraft() {
    try {
    // Save form data to localStorage as draft
    const formData = {
            product_name: document.getElementById('productName')?.value || '',
            category_id: document.getElementById('category')?.value || '',
            description: document.getElementById('description')?.value || '',
            price: document.getElementById('price')?.value || '',
            original_price: document.getElementById('originalPrice')?.value || '',
            stock_quantity: document.getElementById('stockQuantity')?.value || '1',
            min_order: document.getElementById('minOrder')?.value || '',
            condition: document.getElementById('condition')?.value || '',
            weight: document.getElementById('weight')?.value || '',
            weight_unit: document.getElementById('weightUnit')?.value || 'kg',
            is_negotiable: document.getElementById('isNegotiable')?.checked || false,
            is_rental: document.getElementById('isRental')?.checked || false,
            enable_rental_daily: document.getElementById('enableRentalDaily')?.checked || false,
            enable_rental_weekly: document.getElementById('enableRentalWeekly')?.checked || false,
            enable_rental_monthly: document.getElementById('enableRentalMonthly')?.checked || false,
            rental_price_daily: document.getElementById('rentalPriceDaily')?.value || '',
            rental_price_weekly: document.getElementById('rentalPriceWeekly')?.value || '',
            rental_price_monthly: document.getElementById('rentalPriceMonthly')?.value || '',
            // Save specifications
            specifications: Array.from(document.querySelectorAll('.specification-item')).map(item => ({
                name: item.querySelector('.spec-name')?.value || '',
                value: item.querySelector('.spec-value')?.value || ''
            })).filter(spec => spec.name && spec.value),
            saved_at: new Date().toISOString()
    };
    
    localStorage.setItem('listingDraft', JSON.stringify(formData));
        
        if (typeof KaruruUtils !== 'undefined' && KaruruUtils.showNotification) {
            KaruruUtils.showNotification('下書きを保存しました', 'success');
        } else {
            alert('下書きを保存しました');
        }
    } catch (error) {
        console.error('Error saving draft:', error);
    }
}

// Load draft on page load
function loadDraft() {
    try {
        const draftData = localStorage.getItem('listingDraft');
        if (draftData) {
            const draft = JSON.parse(draftData);
            
            // Load basic fields
            if (draft.product_name && document.getElementById('productName')) {
                document.getElementById('productName').value = draft.product_name;
            }
            if (draft.category_id && document.getElementById('category')) {
                document.getElementById('category').value = draft.category_id;
            }
            if (draft.description && document.getElementById('description')) {
                document.getElementById('description').value = draft.description;
            }
            if (draft.price && document.getElementById('price')) {
                document.getElementById('price').value = draft.price;
            }
            if (draft.original_price && document.getElementById('originalPrice')) {
                document.getElementById('originalPrice').value = draft.original_price;
            }
            if (draft.stock_quantity && document.getElementById('stockQuantity')) {
                document.getElementById('stockQuantity').value = draft.stock_quantity;
            }
            if (draft.min_order && document.getElementById('minOrder')) {
                document.getElementById('minOrder').value = draft.min_order;
            }
            if (draft.condition && document.getElementById('condition')) {
                document.getElementById('condition').value = draft.condition;
            }
            if (draft.weight && document.getElementById('weight')) {
                document.getElementById('weight').value = draft.weight;
            }
            if (draft.weight_unit && document.getElementById('weightUnit')) {
                document.getElementById('weightUnit').value = draft.weight_unit;
            }
            if (draft.is_negotiable && document.getElementById('isNegotiable')) {
                document.getElementById('isNegotiable').checked = draft.is_negotiable;
            }
            if (draft.is_rental && document.getElementById('isRental')) {
                document.getElementById('isRental').checked = draft.is_rental;
                if (draft.is_rental) {
                    document.getElementById('rentalSettings').style.display = 'block';
                }
            }
            
            // Load rental enable checkboxes
            if (draft.enable_rental_daily && document.getElementById('enableRentalDaily')) {
                document.getElementById('enableRentalDaily').checked = draft.enable_rental_daily;
                if (draft.enable_rental_daily) {
                    document.getElementById('rentalDailyFields').style.display = 'block';
                }
            }
            if (draft.enable_rental_weekly && document.getElementById('enableRentalWeekly')) {
                document.getElementById('enableRentalWeekly').checked = draft.enable_rental_weekly;
                if (draft.enable_rental_weekly) {
                    document.getElementById('rentalWeeklyFields').style.display = 'block';
                }
            }
            if (draft.enable_rental_monthly && document.getElementById('enableRentalMonthly')) {
                document.getElementById('enableRentalMonthly').checked = draft.enable_rental_monthly;
                if (draft.enable_rental_monthly) {
                    document.getElementById('rentalMonthlyFields').style.display = 'block';
                }
            }
            
            // Load rental prices
            if (draft.rental_price_daily && document.getElementById('rentalPriceDaily')) {
                document.getElementById('rentalPriceDaily').value = draft.rental_price_daily;
            }
            if (draft.rental_price_weekly && document.getElementById('rentalPriceWeekly')) {
                document.getElementById('rentalPriceWeekly').value = draft.rental_price_weekly;
            }
            if (draft.rental_price_monthly && document.getElementById('rentalPriceMonthly')) {
                document.getElementById('rentalPriceMonthly').value = draft.rental_price_monthly;
            }
            
            // Load specifications
            if (draft.specifications && draft.specifications.length > 0) {
                const container = document.getElementById('specificationsContainer');
                if (container) {
                    // Clear existing specifications except first one
                    const existingSpecs = container.querySelectorAll('.specification-item');
                    existingSpecs.forEach((spec, index) => {
                        if (index === 0) {
                            // Update first spec
                            const nameInput = spec.querySelector('.spec-name');
                            const valueInput = spec.querySelector('.spec-value');
                            if (draft.specifications[0]) {
                                if (nameInput) nameInput.value = draft.specifications[0].name;
                                if (valueInput) valueInput.value = draft.specifications[0].value;
                            }
                        } else {
                            spec.remove();
                        }
                    });
                    
                    // Add remaining specifications
                    for (let i = 1; i < draft.specifications.length; i++) {
                        addSpecification();
                        const newSpecs = container.querySelectorAll('.specification-item');
                        const lastSpec = newSpecs[newSpecs.length - 1];
                        const nameInput = lastSpec.querySelector('.spec-name');
                        const valueInput = lastSpec.querySelector('.spec-value');
                        if (nameInput) nameInput.value = draft.specifications[i].name;
                        if (valueInput) valueInput.value = draft.specifications[i].value;
                    }
                }
            }
        }
    } catch (error) {
        console.error('Error loading draft:', error);
    }
}

