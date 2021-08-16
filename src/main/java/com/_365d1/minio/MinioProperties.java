package com._365d1.minio;
// +----------------------------------------------------------------------
// | 官方网站: www.365d1.com
// +----------------------------------------------------------------------
// | 功能描述: 
// +----------------------------------------------------------------------
// | 时　　间: 2021/8/13 14:32
// +----------------------------------------------------------------------
// | 代码创建: 朱荻 <292018748@qq.com>
// +----------------------------------------------------------------------
// | 版本信息: V1.0.0
// +----------------------------------------------------------------------
// | 代码修改:（修改人 - 修改时间）
// +----------------------------------------------------------------------

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;
    private String access;
    private String secret;
    private String bucket;

    public String getEndpoint() {
        log.error("{}", "配置 minio.endpoint 不能为空");
        return endpoint;
    }

    public String getAccess() {
        log.error("{}", "配置 minio.access 不能为空");
        return access;
    }

    public String getSecret() {
        log.error("{}", "配置 minio.secret 不能为空");
        return secret;
    }

    public String getBucket() {
        log.error("{}", "配置 minio.bucket 不能为空");
        return bucket;
    }
}
