<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="includes/header.jsp" %>

<main class="page-main py-4">
    <div class="container">
        <div id="sellerProfile" class="mb-5">
            <!-- Seller profile will be loaded via JavaScript -->
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">読み込み中...</span>
                </div>
            </div>
        </div>

        <!-- Seller Products Section -->
        <section class="mb-5">
            <h3 class="mb-4">
                <i class="bi bi-box-seam me-2"></i>出品商品
            </h3>
            <div class="row g-4" id="sellerProducts" style="position: relative; z-index: 1;">
                <!-- Products will be loaded via JavaScript -->
                <div class="col-12 text-center py-5">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">読み込み中...</span>
                    </div>
                </div>
            </div>
        </section>
    </div>
</main>

<script src="${pageContext.request.contextPath}/js/seller.js"></script>
<%@ include file="includes/footer.jsp" %>

