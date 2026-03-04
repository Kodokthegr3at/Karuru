// Settings page JavaScript

document.addEventListener('DOMContentLoaded', function() {
    loadAccountInfo();
    setupNavigation();
    setupForms();
});

function setupNavigation() {
    const navLinks = document.querySelectorAll('.nav-link[data-section]');
    const sections = document.querySelectorAll('.settings-section');
    
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            const targetSection = this.getAttribute('data-section');
            
            // Update active nav
            navLinks.forEach(nl => nl.classList.remove('active'));
            this.classList.add('active');
            
            // Show target section
            sections.forEach(s => s.classList.add('d-none'));
            document.getElementById(targetSection).classList.remove('d-none');
        });
    });
}

async function loadAccountInfo() {
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/SettingsServlet?action=getSettings`);
        if (!response.ok) {
            throw new Error('Failed to load settings');
        }
        
        const data = await response.json();
        if (data.success && data.settings) {
            const settings = data.settings;
            document.getElementById('username').value = settings.username || '';
            document.getElementById('email').value = settings.email || '';
        }
    } catch (error) {
        console.error('Error loading account info:', error);
    }
}

function setupForms() {
    const accountForm = document.getElementById('accountForm');
    const passwordForm = document.getElementById('passwordForm');
    const notificationForm = document.getElementById('notificationForm');
    const privacyForm = document.getElementById('privacyForm');
    
    if (accountForm) {
        accountForm.addEventListener('submit', handleAccountUpdate);
    }
    
    if (passwordForm) {
        passwordForm.addEventListener('submit', handlePasswordChange);
    }
    
    if (notificationForm) {
        notificationForm.addEventListener('submit', handleNotificationUpdate);
    }
    
    if (privacyForm) {
        privacyForm.addEventListener('submit', handlePrivacyUpdate);
    }
}

async function handleAccountUpdate(e) {
    e.preventDefault();
    
    const formData = {
        action: 'updateAccount',
        email: document.getElementById('email').value
    };
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/SettingsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        const result = await response.json();
        if (result.success) {
        }
    } catch (error) {
        console.error('Error updating account:', error);
    }
}

async function handlePasswordChange(e) {
    e.preventDefault();
    
    const currentPassword = document.getElementById('currentPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const confirmNewPassword = document.getElementById('confirmNewPassword').value;
    
    if (newPassword !== confirmNewPassword) {
        return;
    }
    
    const formData = {
        action: 'changePassword',
        currentPassword: currentPassword,
        newPassword: newPassword
    };
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/SettingsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        const result = await response.json();
        if (result.success) {
            document.getElementById('passwordForm').reset();
        } else {
        }
    } catch (error) {
        console.error('Error changing password:', error);
    }
}

async function handleNotificationUpdate(e) {
    e.preventDefault();
    
    const formData = {
        action: 'updateNotifications',
        email_notifications: document.getElementById('emailNotifications').checked,
        order_notifications: document.getElementById('orderNotifications').checked,
        message_notifications: document.getElementById('messageNotifications').checked
    };
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/SettingsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        const result = await response.json();
        if (result.success) {
        }
    } catch (error) {
        console.error('Error updating notifications:', error);
    }
}

async function handlePrivacyUpdate(e) {
    e.preventDefault();
    
    const formData = {
        action: 'updatePrivacy',
        profile_public: document.getElementById('profilePublic').checked,
        show_email: document.getElementById('showEmail').checked
    };
    
    try {
        const response = await fetch(`${window.CONTEXT_PATH}/SettingsServlet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        const result = await response.json();
        if (result.success) {
        }
    } catch (error) {
        console.error('Error updating privacy:', error);
    }
}

