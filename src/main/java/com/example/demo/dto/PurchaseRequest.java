package com.example.demo.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class PurchaseRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "商品列表不能为空")
    private List<PurchaseItem> items;

    // 静态内部类
    public static class PurchaseItem {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @Min(value = 1, message = "购买数量必须大于0")
        private Integer quantity;

        // getter和setter
        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
