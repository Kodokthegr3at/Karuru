// Login and registration pages. Login is a plain form post; registration answers with JSON.

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-toggle-password]').forEach(button => {
        const input = document.getElementById(button.dataset.togglePassword);
        button.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.querySelector('.bi').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
            button.setAttribute('aria-label', show ? 'パスワードを隠す' : 'パスワードを表示');
        });
    });

    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        const confirm = registerForm.confirmPassword;
        const checkMatch = () => confirm.setCustomValidity(
            confirm.value && confirm.value !== registerForm.password.value ? 'パスワードが一致しません' : '');
        confirm.addEventListener('input', checkMatch);
        registerForm.password.addEventListener('input', checkMatch);
        registerForm.addEventListener('submit', register);
    }
});

async function register(event) {
    event.preventDefault();
    const form = event.target;
    const errorBox = document.getElementById('registerError');
    errorBox.hidden = true;
    if (!form.reportValidity()) return;

    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    try {
        const data = await KaruruUtils.apiFetch(form.action, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(new FormData(form))
        });
        if (data.emailSent) {
            window.location.href = `${window.CONTEXT_PATH}/login.jsp?info=check_email`;
            return;
        }
        errorBox.textContent = data.message;
        errorBox.hidden = false;
    } catch (error) {
        errorBox.textContent = error.message;
        errorBox.hidden = false;
        button.disabled = false;
    }
}
