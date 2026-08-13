package cn.iocoder.yudao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 无头 BPM 服务启动类。
 */
@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${yudao.info.base-package}
@SpringBootApplication(scanBasePackages = "${yudao.info.base-package}.module")
public class YudaoBpmApplication {

    public static void main(String[] args) {
        SpringApplication.run(YudaoBpmApplication.class, args);
    }

}
