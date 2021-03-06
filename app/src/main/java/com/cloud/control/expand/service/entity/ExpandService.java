package com.cloud.control.expand.service.entity;

/**
 * @author wangyou
 * @desc: 扩展服务枚举类
 * @date :2021/2/26
 */
public enum ExpandService {
    /*IP代理；虚拟定位；一键新机；虚拟行为场景；多窗口推流；文字识别；日志调试*/
    IP_PROXY(1),VIRTUAL_LOCATION(2),CHANGE_MACHINE(3),VIRTUAL_SCENE(4),MUL_WINDOW(5),OCR(6),LOG_DEBUG(7),ROOT_PATTERN(8);

    private int typeId;

    ExpandService(int typeId) {
        this.typeId = typeId;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }
    public static ExpandService getExpandService(int typeId){
        for (ExpandService service : ExpandService.values()){
            if (service.getTypeId() == typeId){
                return service;
            }
        }
        return null;
    }
}
