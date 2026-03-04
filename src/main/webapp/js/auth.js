// Authentication pages JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    
    if (loginForm) {
        setupLoginForm();
        // Jangan prevent default, biarkan form submit normal
        // LoginServlet akan handle redirect
        loginForm.addEventListener('submit', function(e) {
            // Hanya validasi, tidak prevent default
            const emailOrUser = document.getElementById('emailOrUser').value.trim();
            const password = document.getElementById('password').value;
            
            if (!emailOrUser || !password) {
                e.preventDefault();
                const errorDiv = document.getElementById('loginError');
                errorDiv.textContent = 'すべての項目を入力してください';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
                return false;
            }
            
            // Show loading state
            const submitBtn = document.getElementById('loginSubmitBtn');
            const submitText = document.getElementById('loginSubmitText');
            const submitLoader = document.getElementById('loginSubmitLoader');
            
            if (submitBtn) submitBtn.disabled = true;
            if (submitText) submitText.style.display = 'none';
            if (submitLoader) {
                submitLoader.classList.remove('d-none');
                submitLoader.style.display = 'inline-block';
            }
            
            // Form akan submit normal, LoginServlet akan redirect
        });
    }
    
    if (registerForm) {
        setupRegisterForm();
        registerForm.addEventListener('submit', handleRegister);
    }
    
    // Setup password toggle
    setupPasswordToggle();
    
    // Check URL parameters for errors
    checkUrlParams();
});

function setupLoginForm() {
    const emailOrUserInput = document.getElementById('emailOrUser');
    const passwordInput = document.getElementById('password');
    const usernameHelp = document.getElementById('usernameHelp');
    const passwordHelp = document.getElementById('passwordHelp');
    
    if (emailOrUserInput) {
        emailOrUserInput.addEventListener('input', function() {
            const value = this.value.trim();
            if (value.length > 0) {
                usernameHelp.textContent = '';
                this.classList.remove('error');
            }
        });
    }
    
    if (passwordInput) {
        passwordInput.addEventListener('input', function() {
            if (this.value.length > 0) {
                passwordHelp.textContent = '';
                this.classList.remove('error');
            }
        });
    }
}

function setupRegisterForm() {
    const usernameInput = document.getElementById('username');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    
    // Username validation
    if (usernameInput) {
        usernameInput.addEventListener('input', function() {
            validateUsername(this, document.getElementById('usernameHelp'));
        });
    }
    
    // Email validation
    if (emailInput) {
        emailInput.addEventListener('input', function() {
            validateEmail(this, document.getElementById('emailHelp'));
        });
    }
    
    // Password strength
    if (passwordInput) {
        passwordInput.addEventListener('input', function() {
            checkPasswordStrength();
            checkPasswordMatch();
        });
    }
    
    // Password confirmation
    if (confirmPasswordInput) {
        confirmPasswordInput.addEventListener('input', function() {
            checkPasswordMatch();
        });
    }
}

function validateUsername(input, helpElement) {
    const value = input.value.trim();
    const pattern = /^[a-zA-Z0-9_]+$/;
    
    if (value.length < 3) {
        helpElement.textContent = 'ユーザー名は3文字以上必要です';
        helpElement.style.color = 'var(--danger-color)';
        input.classList.add('error');
        return false;
    } else if (value.length > 20) {
        helpElement.textContent = 'ユーザー名は20文字以内で入力してください';
        helpElement.style.color = 'var(--danger-color)';
        input.classList.add('error');
        return false;
    } else if (!pattern.test(value)) {
        helpElement.textContent = '英数字とアンダースコアのみ使用可能です';
        helpElement.style.color = 'var(--danger-color)';
        input.classList.add('error');
        return false;
    } else {
        helpElement.textContent = '✓ 有効なユーザー名です';
        helpElement.style.color = 'var(--success-color)';
        input.classList.remove('error');
        return true;
    }
}

function validateEmail(input, helpElement) {
    const value = input.value.trim();
    const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (!pattern.test(value)) {
        helpElement.textContent = '有効なメールアドレスを入力してください';
        helpElement.style.color = 'var(--danger-color)';
        input.classList.add('error');
        return false;
    } else {
        helpElement.textContent = '✓ 有効なメールアドレスです';
        helpElement.style.color = 'var(--success-color)';
        input.classList.remove('error');
        return true;
    }
}

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
    const password = document.getElementById('password');
    const confirmPassword = document.getElementById('confirmPassword');
    const helpElement = document.getElementById('confirmPasswordHelp');
    
    if (!password || !confirmPassword || !helpElement) return;
    
    if (confirmPassword.value.length === 0) {
        helpElement.textContent = '';
        return;
    }
    
    if (password.value === confirmPassword.value) {
        helpElement.textContent = '✓ パスワードが一致しています';
        helpElement.style.color = 'var(--success-color)';
        confirmPassword.classList.remove('error');
    } else {
        helpElement.textContent = '✗ パスワードが一致しません';
        helpElement.style.color = 'var(--danger-color)';
        confirmPassword.classList.add('error');
    }
}

function setupPasswordToggle() {
    const togglePassword = document.getElementById('togglePassword');
    const toggleConfirmPassword = document.getElementById('toggleConfirmPassword');
    
    if (togglePassword) {
        togglePassword.addEventListener('click', function() {
            const input = document.getElementById('password');
            const icon = this.querySelector('i');
            if (input.type === 'password') {
                input.type = 'text';
                icon.classList.remove('bi-eye');
                icon.classList.add('bi-eye-slash');
            } else {
                input.type = 'password';
                icon.classList.remove('bi-eye-slash');
                icon.classList.add('bi-eye');
            }
        });
    }
    
    if (toggleConfirmPassword) {
        toggleConfirmPassword.addEventListener('click', function() {
            const input = document.getElementById('confirmPassword');
            const icon = this.querySelector('i');
            if (input.type === 'password') {
                input.type = 'text';
                icon.classList.remove('bi-eye');
                icon.classList.add('bi-eye-slash');
            } else {
                input.type = 'password';
                icon.classList.remove('bi-eye-slash');
                icon.classList.add('bi-eye');
            }
        });
    }
}

function checkUrlParams() {
    const urlParams = new URLSearchParams(window.location.search);
    const error = urlParams.get('error');
    const attempts = urlParams.get('attempts');
    const minutes = urlParams.get('minutes');
    const loginError = document.getElementById('loginError');
    const registerError = document.getElementById('registerError');
    const errorDiv = loginError || registerError;
    
    if (error && errorDiv) {
        const errorMessages = {
            'empty': 'すべての項目を入力してください',
            'empty_fields': 'すべての必須項目を入力してください',
            'invalid': 'ユーザー名またはパスワードが正しくありません',
            'invalid_password': 'パスワードが正しくありません',
            'invalid_email': '有効なメールアドレスを入力してください',
            'invalid_username': 'ユーザー名は3-50文字の英数字とアンダースコアのみ使用可能です',
            'password_short': 'パスワードは6文字以上必要です',
            'password_mismatch': 'パスワードが一致しません',
            'email_exists': 'このメールアドレスは既に登録されています',
            'username_exists': 'このユーザー名は既に使用されています',
            'not_found': 'ユーザーが見つかりません',
            'account_locked': 'アカウントがロックされています。30分後に再度お試しください',
            'not_verified': 'メールアドレスの確認が完了していません',
            'registration_failed': '登録に失敗しました',
            'database_error': 'データベースエラーが発生しました',
            'server_error': 'サーバーエラーが発生しました'
        };
        
        let errorMessage = errorMessages[error] || 'エラーが発生しました';
        
        // Show remaining attempts if attempts parameter is present and error is invalid_password
        if (error === 'invalid_password' && attempts) {
            const remainingAttempts = 5 - parseInt(attempts);
            if (remainingAttempts > 0) {
                errorMessage = `パスワードが正しくありません。あと${remainingAttempts}回試行できます（5回失敗するとアカウントがロックされます）`;
            } else {
                errorMessage = 'パスワードが正しくありません。アカウントがロックされました。30分後に再度お試しください';
            }
        }
        
        // Show remaining minutes if account is locked
        if (error === 'account_locked' && minutes) {
            const minutesLeft = parseInt(minutes);
            if (minutesLeft > 0) {
                errorMessage = `アカウントがロックされています。あと約${minutesLeft}分後に再度お試しください`;
            } else {
                errorMessage = 'アカウントがロックされています。しばらくしてから再度お試しください';
            }
        }
        
        errorDiv.textContent = errorMessage;
        errorDiv.classList.remove('d-none');
        errorDiv.classList.add('show');
        
        // Scroll to error message
        errorDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    
    // Check for success message
    const loginSuccess = urlParams.get('login');
    const registered = urlParams.get('registered');
    const info = urlParams.get('info');
    
    if (loginSuccess === 'success') {
        const successDiv = document.getElementById('loginSuccess');
        if (successDiv) {
            successDiv.innerHTML = '<i class="bi bi-check-circle me-2"></i>ログインに成功しました';
            successDiv.classList.remove('d-none');
            successDiv.classList.add('show');
            
            // Initialize WebSocket after successful login
            setTimeout(() => {
                if (typeof window.initWebSockets === 'function') {
                    window.initWebSockets();
                }
            }, 500);
            
            // Hide after 3 seconds
            setTimeout(() => {
                successDiv.classList.add('d-none');
            }, 3000);
        }
    }
    
    // Show info message when redirected from register (just info, not success)
    if (info === 'check_email') {
        const successDiv = document.getElementById('loginSuccess');
        if (successDiv) {
            successDiv.innerHTML = '<i class="bi bi-info-circle me-2"></i>登録が完了しました。確認メールを送信しました。メールボックスを確認してアカウントを有効化してください。';
            successDiv.classList.remove('d-none');
            successDiv.classList.add('show');
            
            // Scroll to success message
            successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            
            // Hide after 5 seconds
            setTimeout(() => {
                successDiv.classList.add('d-none');
            }, 5000);
        }
    }
    
    // Legacy support for registered parameter (if still used)
    if (registered === 'true') {
        const successDiv = document.getElementById('loginSuccess');
        if (successDiv) {
            successDiv.innerHTML = '<i class="bi bi-info-circle me-2"></i>登録が完了しました。確認メールを送信しました。メールボックスを確認してアカウントを有効化してください。';
            successDiv.classList.remove('d-none');
            successDiv.classList.add('show');
            
            // Scroll to success message
            successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            
            // Hide after 5 seconds
            setTimeout(() => {
                successDiv.classList.add('d-none');
            }, 5000);
        }
    }
    
    // Check for success parameter (can be verified or password_reset)
    const successParam = urlParams.get('success');
    
    // Check for account verification success (after clicking verify link)
    if (successParam === 'verified') {
        const successDiv = document.getElementById('loginSuccess');
        if (successDiv) {
            successDiv.innerHTML = '<i class="bi bi-check-circle me-2"></i>アカウントの確認が完了しました！登録ありがとうございます。ログインしてください。';
            successDiv.classList.remove('d-none');
            successDiv.classList.add('show');
            
            // Scroll to success message
            successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            
            // Hide after 5 seconds
            setTimeout(() => {
                successDiv.classList.add('d-none');
            }, 5000);
        }
    }
    // Check for password reset success
    else if (successParam === 'password_reset') {
        const successDiv = document.getElementById('loginSuccess');
        if (successDiv) {
            successDiv.innerHTML = '<i class="bi bi-check-circle me-2"></i>パスワードが正常にリセットされました。新しいパスワードでログインしてください。';
            successDiv.classList.remove('d-none');
            successDiv.classList.add('show');
            
            // Scroll to success message
            successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            
            // Hide after 5 seconds
            setTimeout(() => {
                successDiv.classList.add('d-none');
            }, 5000);
        }
    }
}

// handleLogin function sudah tidak digunakan
// Form submit dilakukan langsung tanpa preventDefault
// LoginServlet akan handle redirect dengan sendRedirect()

async function handleRegister(e) {
    e.preventDefault();
    
    const errorDiv = document.getElementById('registerError');
    const successDiv = document.getElementById('registerSuccess');
    const submitBtn = document.getElementById('registerSubmitBtn');
    const submitText = document.getElementById('registerSubmitText');
    const submitLoader = document.getElementById('registerSubmitLoader');
    
    // Clear previous messages
    errorDiv.classList.remove('show');
    successDiv.classList.remove('show');
    errorDiv.textContent = '';
    successDiv.textContent = '';
    
    // Validate form
    const username = document.getElementById('username').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const agreeTerms = document.getElementById('agreeTerms').checked;
    
    // Validate username
    if (!validateUsername(document.getElementById('username'), document.getElementById('usernameHelp'))) {
        errorDiv.textContent = 'ユーザー名を正しく入力してください';
        errorDiv.classList.add('show');
        return;
    }
    
    // Validate email
    if (!validateEmail(document.getElementById('email'), document.getElementById('emailHelp'))) {
        errorDiv.textContent = 'メールアドレスを正しく入力してください';
        errorDiv.classList.add('show');
        return;
    }
    
    // Validate password
    if (password.length < 6) {
        errorDiv.textContent = 'パスワードは6文字以上必要です';
        errorDiv.classList.add('show');
        return;
    }
    
    // Validate password match
    if (password !== confirmPassword) {
        errorDiv.textContent = 'パスワードが一致しません';
        errorDiv.classList.add('show');
        return;
    }
    
    // Validate terms
    if (!agreeTerms) {
        errorDiv.textContent = '利用規約に同意してください';
        errorDiv.classList.add('show');
        return;
    }
    
    // Show loading
    submitBtn.disabled = true;
    submitText.style.display = 'none';
    submitLoader.style.display = 'inline-block';
    
    // Prepare form data as URL-encoded (not FormData)
    const formData = new URLSearchParams();
    formData.append('username', username);
    formData.append('email', email);
    formData.append('password', password);
    // confirmPassword is not sent to server, only used for validation
    
    const fullName = document.getElementById('fullName')?.value.trim() || '';
    const phone = document.getElementById('phone')?.value.trim() || '';
    
    if (fullName) formData.append('fullName', fullName);
    if (phone) formData.append('phone', phone);
    
    try {
        const contextPath = window.CONTEXT_PATH || '';
        const url = contextPath ? `${contextPath}/RegisterServlet` : '/RegisterServlet';
        
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
            },
            body: formData.toString()
        });
        
        // Check response status
        if (response.status === 404) {
            errorDiv.textContent = 'サーバーエラー: RegisterServletが見つかりません。サーバーを再起動してください。';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
            submitBtn.disabled = false;
            submitText.style.display = 'inline';
            submitLoader.style.display = 'none';
            return;
        }
        
        if (response.status === 400) {
            // Try to get error message from response
            try {
                const errorData = await response.json();
                errorDiv.textContent = errorData.message || errorData.error || 'リクエストが無効です。入力内容を確認してください。';
            } catch (e) {
                errorDiv.textContent = 'リクエストが無効です。入力内容を確認してください。';
            }
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
            submitBtn.disabled = false;
            submitText.style.display = 'inline';
            submitLoader.style.display = 'none';
            return;
        }
        
        // Check if response is JSON
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const result = await response.json();
            if (result.success) {
                // Show success message with icon
                const successMessage = result.message || '登録が完了しました。確認メールを送信しました。';
                successDiv.innerHTML = `<i class="bi bi-check-circle me-2"></i>${successMessage}`;
                successDiv.classList.remove('d-none');
                successDiv.classList.add('show');
                
                // Scroll to success message
                successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                
                // Reset form
                e.target.reset();
                
                // Redirect to login page after 3 seconds
                setTimeout(() => {
                    const contextPath = window.CONTEXT_PATH || '';
                    const loginUrl = contextPath ? `${contextPath}/login.jsp?info=check_email` : '/login.jsp?info=check_email';
                    window.location.href = loginUrl;
                }, 3000);
            } else {
                errorDiv.textContent = result.message || result.error || '登録に失敗しました';
                errorDiv.classList.remove('d-none');
                errorDiv.classList.add('show');
                submitBtn.disabled = false;
                submitText.style.display = 'inline';
                submitLoader.style.display = 'none';
            }
        } else {
            // Unexpected response type
            errorDiv.textContent = 'サーバーからの応答が予期しない形式です。';
            errorDiv.classList.remove('d-none');
            errorDiv.classList.add('show');
            submitBtn.disabled = false;
            submitText.style.display = 'inline';
            submitLoader.style.display = 'none';
        }
    } catch (error) {
        errorDiv.textContent = '登録に失敗しました。ネットワーク接続を確認してください。';
        errorDiv.classList.remove('d-none');
        errorDiv.classList.add('show');
    } finally {
        submitBtn.disabled = false;
        submitText.style.display = 'inline';
        submitLoader.style.display = 'none';
    }
}

// Initialize WebSocket connections after login
function initializeWebSockets() {
    // This will be called from main.js after page load if user is logged in
    if (typeof window.initWebSockets === 'function') {
        window.initWebSockets();
    }
}

