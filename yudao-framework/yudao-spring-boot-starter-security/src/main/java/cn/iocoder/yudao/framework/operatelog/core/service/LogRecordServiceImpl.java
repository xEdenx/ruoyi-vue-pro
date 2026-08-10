package cn.iocoder.yudao.framework.operatelog.core.service;

import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.mzt.logapi.beans.LogRecord;
import com.mzt.logapi.service.ILogRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 操作日志 ILogRecordService 实现类
 *
 * 将操作日志写入应用日志，后续由 Portal 审计或 OTel 采集。
 *
 * @author HUIHUI
 */
@Slf4j
public class LogRecordServiceImpl implements ILogRecordService {

    @Override
    public void record(LogRecord logRecord) {
        try {
            LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
            HttpServletRequest request = ServletUtils.getRequest();
            log.info("[Portal 审计待投递][operation][traceId={}][userId={}][userType={}][type={}][subType={}]"
                            + "[bizNo={}][action={}][extra={}][method={}][url={}][ip={}][userAgent={}]",
                    TracerUtils.getTraceId(), loginUser != null ? loginUser.getId() : null,
                    loginUser != null ? loginUser.getUserType() : null, logRecord.getType(), logRecord.getSubType(),
                    logRecord.getBizNo(), logRecord.getAction(), logRecord.getExtra(),
                    request != null ? request.getMethod() : null, request != null ? request.getRequestURI() : null,
                    request != null ? ServletUtils.getClientIP(request) : null,
                    request != null ? ServletUtils.getUserAgent(request) : null);
        } catch (Throwable ex) {
            log.error("[record][操作日志输出失败]", ex);
        }
    }

    @Override
    public List<LogRecord> queryLog(String bizNo, String type) {
        throw new UnsupportedOperationException("使用 OperateLogApi 进行操作日志的查询");
    }

    @Override
    public List<LogRecord> queryLogByBizNo(String bizNo, String type, String subType) {
        throw new UnsupportedOperationException("使用 OperateLogApi 进行操作日志的查询");
    }

}
