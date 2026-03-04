// Reset Password JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('resetPasswordForm');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    
    if (form) {
        form.addEventListener('submit', handleResetPassword);
    }
    
    // Password strength checker
    if (passwordInput) {
        passwordInput.addEventListener('input', checkPasswordStrength);
    }
    
    // Password match checker
    if (confirmPasswordInput) {
        confirmPasswordInput.addEventListener('input', checkPasswordMatch);
    }
    
    // Check if code exists
    const codeInput = document.getElementById('code');
    if (codeInput) {
        const code = codeInput.value;
        if (!code || code.trim() === '') {
            const errorDiv = document.getElementById('resetPasswordError');
            if (errorDiv) {
                errorDiv.textContent = '無効なリセットリンクです。';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
            }
            if (form) {
                form.style.display = 'none';
            }
        }
    }
    
    // Check URL parameters on page load
    const urlParams = new URLSearchParams(window.location.search);
    const errorDiv = document.getElementById('resetPasswordError');
    const successDiv = document.getElementById('resetPasswordSuccess');
    
    if (urlParams.has('error')) {
        const error = urlParams.get('error');
        let errorMessage = 'パスワードリセットに失敗しました。';
        
        switch(error) {
            case 'empty_password':
                errorMessage = 'パスワードを入力してください。';
                break;
            case 'password_short':
                errorMessage = 'パスワードは6文字以上必要です。';
                break;
            case 'password_mismatch':
                errorMessage = 'パスワードが一致しません。';
                break;
            case 'reset_token_expired':
                errorMessage = 'リセットリンクの有効期限が切れています。';
                break;
            case 'database_error':
                errorMessage = 'データベースエラーが発生しました。';
                break;
            case 'server_error':
                errorMessage = 'サーバーエラーが発生しました。';
                break;
        }
        
        if (errorDiv) {
            errorDiv.textContent = errorMessage;
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
    }
});

function checkPasswordStrength() {
    const password = document.getElementById('password').value;
    const strengthFill = document.getElementById('strengthFill');
    const strengthText = document.getElementById('strengthText');
    
    if (!strengthFill || !strengthText) return;
    
    let strength = 0;
    let strengthLabel = '';
    let strengthColor = '';
    
    if (password.length >= 6) strength++;
    if (password.length >= 8) strength++;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++;
    if (/\d/.test(password)) strength++;
    if (/[^a-zA-Z\d]/.test(password)) strength++;
    
    switch(strength) {
        case 0:
        case 1:
            strengthLabel = '弱い';
            strengthColor = '#f44336';
            break;
        case 2:
        case 3:
            strengthLabel = '普通';
            strengthColor = '#ff9800';
            break;
        case 4:
        case 5:
            strengthLabel = '強い';
            strengthColor = '#4CAF50';
            break;
    }
    
    strengthFill.style.width = (strength * 20) + '%';
    strengthFill.style.backgroundColor = strengthColor;
    strengthText.textContent = 'パスワード強度: ' + strengthLabel;
}

function checkPasswordMatch() {
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const matchText = document.getElementById('confirmPasswordHelp');
    
    if (!matchText) return;
    
    if (confirmPassword.length === 0) {
        matchText.textContent = '';
        return;
    }
    
    if (password === confirmPassword) {
        matchText.textContent = '✓ パスワードが一致しています';
        matchText.style.color = '#4CAF50';
    } else {
        matchText.textContent = '✗ パスワードが一致しません';
        matchText.style.color = '#f44336';
    }
}

async function handleResetPassword(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('resetPasswordError');
    const successDiv = document.getElementById('resetPasswordSuccess');
    const submitBtn = document.getElementById('submitBtn');
    const submitText = document.getElementById('submitText');
    const submitLoader = document.getElementById('submitLoader');
    
    // Clear previous messages
    if (errorDiv) {
        errorDiv.classList.remove('show');
        errorDiv.classList.add('d-none');
        errorDiv.textContent = '';
    }
    if (successDiv) {
        successDiv.classList.remove('show');
        successDiv.classList.add('d-none');
        successDiv.textContent = '';
    }
    
    // Validate passwords match
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    
    if (password !== confirmPassword) {
        if (errorDiv) {
            errorDiv.textContent = 'パスワードが一致しません';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        return;
    }
    
    // Show loading
    if (submitBtn) submitBtn.disabled = true;
    if (submitText) submitText.style.display = 'none';
    if (submitLoader) {
        submitLoader.classList.remove('d-none');
        submitLoader.style.display = 'inline-block';
    }
    
    // Prepare form data as URL-encoded
    const formData = new URLSearchParams();
    const codeInput = document.getElementById('code');
    if (!codeInput || !codeInput.value) {
        if (errorDiv) {
            errorDiv.textContent = '無効なリセットリンクです。';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
        if (submitBtn) submitBtn.disabled = false;
        if (submitText) submitText.style.display = 'inline';
        if (submitLoader) {
            submitLoader.classList.add('d-none');
            submitLoader.style.display = 'none';
        }
        return;
    }
    const code = codeInput.value;
    formData.append('code', code);
    formData.append('password', password);
    formData.append('confirmPassword', confirmPassword);
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const url = contextPath ? `${contextPath}/ResetPasswordServlet` : '/ResetPasswordServlet';
        
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            },
            body: formData.toString(),
            redirect: 'follow' // Follow redirects
        });
        
        // Check response status
        if (response.ok || response.status === 302 || response.status === 301 || response.redirected) {
            // Show success message first
            if (successDiv) {
                successDiv.innerHTML = '<i class="bi bi-check-circle me-2"></i>パスワードが正常にリセットされました。ログインページにリダイレクトします。';
                successDiv.classList.remove('d-none');
                successDiv.classList.add('show');
                
                // Scroll to success message
                successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
            
            // Wait a bit to show message, then redirect
            setTimeout(() => {
                // Check if response was redirected
                if (response.redirected && response.url) {
                    window.location.href = response.url;
                } else {
                    // Fallback: redirect to login with success parameter
                    const contextPath = window.CONTEXT_PATH || '';
                    const loginUrl = contextPath ? `${contextPath}/login.jsp?success=password_reset` : '/login.jsp?success=password_reset';
                    window.location.href = loginUrl;
                }
            }, 2000);
            return;
        }
        
        // If response is not OK, show error
        if (errorDiv) {
            errorDiv.textContent = 'パスワードリセットに失敗しました。もう一度お試しください。';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
        }
    } catch (error) {
        console.error('Error:', error);
        if (errorDiv) {
            errorDiv.textContent = 'エラーが発生しました。もう一度お試しください。';
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

