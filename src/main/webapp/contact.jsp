<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="page-main py-5">
    <div class="container">
        <div class="row justify-content-center">
            <div class="col-lg-8">
                <h1 class="fw-bold mb-4">お問い合わせ</h1>
                
                <div class="card card-light">
                    <div class="card-body">
                        <form id="contactForm">
                            <div class="mb-3">
                                <label for="name" class="form-label">お名前 <span class="text-danger">*</span></label>
                                <input type="text" class="form-control form-control-light" 
                                       id="name" name="name" required>
                            </div>
                            
                            <div class="mb-3">
                                <label for="email" class="form-label">メールアドレス <span class="text-danger">*</span></label>
                                <input type="email" class="form-control form-control-light" 
                                       id="email" name="email" required>
                            </div>
                            
                            <div class="mb-3">
                                <label for="subject" class="form-label">件名 <span class="text-danger">*</span></label>
                                <input type="text" class="form-control form-control-light" 
                                       id="subject" name="subject" required>
                            </div>
                            
                            <div class="mb-3">
                                <label for="message" class="form-label">メッセージ <span class="text-danger">*</span></label>
                                <textarea class="form-control form-control-light" 
                                          id="message" name="message" rows="6" required></textarea>
                            </div>
                            
                            <div id="contactError" class="alert alert-danger d-none" role="alert"></div>
                            <div id="contactSuccess" class="alert alert-success d-none" role="alert"></div>
                            
                            <button type="submit" class="btn btn-primary">
                                <i class="bi bi-send"></i> 送信
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>

<script>
document.getElementById('contactForm')?.addEventListener('submit', function(e) {
    e.preventDefault();
    const errorDiv = document.getElementById('contactError');
    const successDiv = document.getElementById('contactSuccess');
    
    errorDiv.classList.add('d-none');
    successDiv.classList.add('d-none');
    
    // Simulate form submission
    setTimeout(() => {
        successDiv.textContent = 'お問い合わせありがとうございます。担当者よりご連絡いたします。';
        successDiv.classList.remove('d-none');
        document.getElementById('contactForm').reset();
    }, 500);
});
</script>
<%@ include file="includes/footer.jsp" %>

