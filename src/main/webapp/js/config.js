// Configuration file for context path
// This will be set in each JSP page before loading other scripts
if (typeof CONTEXT_PATH === 'undefined') {
    // Fallback: try to detect from current path
    const pathParts = window.location.pathname.split('/').filter(p => p);
    window.CONTEXT_PATH = pathParts.length > 0 ? '/' + pathParts[0] : '';
}

