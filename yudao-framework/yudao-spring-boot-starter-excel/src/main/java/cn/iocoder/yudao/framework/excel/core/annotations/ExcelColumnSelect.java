package cn.iocoder.yudao.framework.excel.core.annotations;

import java.lang.annotation.*;

/**
 * 给 Excel 列添加下拉选择数据
 *
 * 数据源由 {@link #functionName()} 指向的 Spring Bean 提供。
 *
 * @author HUIHUI
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ExcelColumnSelect {

    /**
     * @return 获取下拉数据源的方法名称
     */
    String functionName();

}
