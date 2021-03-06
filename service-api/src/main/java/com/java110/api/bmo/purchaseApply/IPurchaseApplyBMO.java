package com.java110.api.bmo.purchaseApply;

import com.alibaba.fastjson.JSONObject;
import com.java110.api.bmo.IApiBaseBMO;
import com.java110.core.context.DataFlowContext;

/**
 * @ClassName IPurchaseApplyBMO
 * @Description TODO
 * @Author wuxw
 * @Date 2020/3/9 23:33
 * @Version 1.0
 * add by wuxw 2020/3/9
 **/
public interface IPurchaseApplyBMO extends IApiBaseBMO {
    /**
     * 添加小区信息
     *
     * @param paramInJson     接口调用放传入入参
     * @param dataFlowContext 数据上下文
     * @return 订单服务能够接受的报文
     */
    public void deletePurchaseApply(JSONObject paramInJson, DataFlowContext dataFlowContext);
    /**
     * 添加小区信息
     *
     * @param paramInJson     接口调用放传入入参
     * @param dataFlowContext 数据上下文
     * @return 订单服务能够接受的报文
     */
    public void addPurchaseApply(JSONObject paramInJson, DataFlowContext dataFlowContext);

    /**
     * 添加采购申请信息
     *
     * @param paramInJson     接口调用放传入入参
     * @param dataFlowContext 数据上下文
     * @return 订单服务能够接受的报文
     */
    public void updatePurchaseApply(JSONObject paramInJson, DataFlowContext dataFlowContext);
}
