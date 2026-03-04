// Forgot Password JavaScript

document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('forgotPasswordForm');
    const emailInput = document.getElementById('email');
    const emailHelp = document.getElementById('emailHelp');
    const errorDiv = document.getElementById('forgotPasswordError');
    const successDiv = document.getElementById('forgotPasswordSuccess');
    
    if (form) {
        form.addEventListener('submit', handleForgotPassword);
    }
    
    // Email validation
    if (emailInput && emailHelp) {
        emailInput.addEventListener('input', function() {
            validateEmail(this, emailHelp);
        });
    }
    
    // Check URL parameters on page load
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('success') || urlParams.has('error')) {
        handleRedirectResponse(urlParams, errorDiv, successDiv, null);
    }
});

function validateEmail(input, helpElement) {
    const value = input.value.trim();
    const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    
    if (value.length === 0) {
        helpElement.textContent = '登録時に使用したメールアドレスを入力してください';
        helpElement.style.color = 'var(--text-muted)';
        input.classList.remove('error');
        return false;
    } else if (!pattern.test(value)) {
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

function handleForgotPassword(e) {
    const errorDiv = document.getElementById('forgotPasswordError');
    const successDiv = document.getElementById('forgotPasswordSuccess');
    const emailInput = document.getElementById('email');
    const emailHelp = document.getElementById('emailHelp');
    
    // Clear previous messages
    errorDiv.classList.add('d-none');
    errorDiv.classList.remove('show');
    successDiv.classList.add('d-none');
    successDiv.classList.remove('show');
    errorDiv.textContent = '';
    successDiv.textContent = '';
    
    // Validate email
    if (!validateEmail(emailInput, emailHelp)) {
        e.preventDefault();
        errorDiv.textContent = '有効なメールアドレスを入力してください';
        errorDiv.classList.remove('d-none');
        errorDiv.classList.add('show');
        return false;
    }
    
    // If validation passes, allow form to submit normally
    // The servlet will redirect back with success/error parameters
    // which will be handled by the page load handler
    return true;
}

function handleRedirectResponse(urlParams, errorDiv, successDiv, form) {
    const contextPath = window.CONTEXT_PATH || '';
    
    if (urlParams.has('success')) {
        const warning = urlParams.get('warning');
        if (warning === 'email_failed') {
            successDiv.innerHTML = '<i class="bi bi-exclamation-triangle me-2"></i>リセットリンクの生成に成功しましたが、メール送信に失敗しました。後でもう一度お試しください。';
        } else {
            successDiv.innerHTML = '<i class="bi bi-check-circle me-2"></i>パスワードリセット用のメールを送信しました。メールボックスを確認してください。';
        }
        successDiv.classList.remove('d-none');
        successDiv.classList.add('show');
        
        // Scroll to success message
        successDiv.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        
        if (form) form.reset();
        
        setTimeout(() => {
            const loginUrl = contextPath ? `${contextPath}/login.jsp` : '/login.jsp';
            window.location.href = loginUrl;
        }, 3000);
    } else if (urlParams.has('error')) {
        const error = urlParams.get('error');
        let errorMessage = 'メール送信に失敗しました。もう一度お試しください。';
        
        switch(error) {
            case 'empty_email':
                errorMessage = 'メールアドレスを入力してください。';
                break;
            case 'invalid_email':
                errorMessage = '有効なメールアドレスを入力してください。';
                break;
            case 'update_failed':
                errorMessage = 'リセットトークンの生成に失敗しました。もう一度お試しください。';
                break;
            case 'database_error':
                errorMessage = 'データベースエラーが発生しました。しばらくしてからもう一度お試しください。';
                break;
            case 'server_error':
                errorMessage = 'サーバーエラーが発生しました。しばらくしてからもう一度お試しください。';
                break;
        }
        
        errorDiv.textContent = errorMessage;
        errorDiv.classList.remove('d-none');
        errorDiv.classList.add('show');
    }
}


