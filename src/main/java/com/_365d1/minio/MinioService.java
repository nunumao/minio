package com._365d1.minio;
// +----------------------------------------------------------------------
// | 官方网站: www.365d1.com
// +----------------------------------------------------------------------
// | 功能描述: 
// +----------------------------------------------------------------------
// | 时　　间: 2021/8/13 14:44
// +----------------------------------------------------------------------
// | 代码创建: 朱荻 <292018748@qq.com>
// +----------------------------------------------------------------------
// | 版本信息: V1.0.0
// +----------------------------------------------------------------------
// | 代码修改:（修改人 - 修改时间）
// +----------------------------------------------------------------------

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

@Slf4j
public class MinioService {

    private String endpoint;
    private String access;
    private String secret;
    private String bucket;
    private MinioClient minioClient;

    public MinioService(String endpoint, String access, String secret, String bucket) {
        this.endpoint = endpoint;
        this.access = access;
        this.secret = secret;
        this.bucket = bucket;
        this.init();
    }

    private void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(this.endpoint)
                .credentials(this.access, this.secret)
                .build();
    }

    /**
     * 获取 Minio Client 实例
     *
     * @return 实例
     */
    public MinioClient getInstance() {
        return this.minioClient;
    }

    /**
     * 获取文件
     *
     * @param name 文件名称
     * @return 文件流
     */
    public InputStream get(String name) {
        try {
            InputStream stream = this.minioClient.getObject(GetObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(name)
                    .build());
            return stream;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 结果
     */
    public MinioResult upload(MultipartFile file) {
        try {
            String suffix = Objects.requireNonNull(file.getOriginalFilename()).substring(file.getOriginalFilename().lastIndexOf(".") + 1);
            String fileName = this.uuid() + "." + suffix;
            this.minioClient.putObject(PutObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return new MinioResult(MinioResult.SUCCESS, fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public MinioResult uploadPart(String name, int index, int total, MultipartFile file) {
        String suffix = "";
        String uuid = "";

        if (!StringUtils.hasLength(name)) {
            suffix = Objects.requireNonNull(file.getOriginalFilename()).substring(file.getOriginalFilename().lastIndexOf(".") + 1);
            uuid = this.uuid();
        } else {
            String[] split = name.split("\\.");
            assert split.length > 2;
            suffix = split[1];
            uuid = split[0];
        }

        try {

            if (!this.existsFolder("temp/" + uuid)) {
                this.createFolder("temp/" + uuid);
            }

            this.minioClient.putObject(PutObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(String.format("temp/%s/%d", uuid, index))
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            if (index >= total) {
                Iterable<Result<Item>> listObjects = this.minioClient.listObjects(ListObjectsArgs.builder()
                        .bucket(this.bucket)
                        .prefix(String.format("temp/%s/", uuid))
                        .build());
                List<ComposeSource> sourceList = new ArrayList<>();
                listObjects.forEach(item -> {
                    try {
                        sourceList.add(ComposeSource.builder()
                                .bucket(this.bucket)
                                .object(item.get().objectName())
                                .build());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                this.minioClient.composeObject(ComposeObjectArgs.builder()
                        .bucket(this.bucket)
                        .object(String.format("%s.%s", uuid, suffix))
                        .sources(sourceList)
                        .build());

                this.removeFolder(String.format("temp/%s", uuid));

                return new MinioResult(MinioResult.SUCCESS, uuid + "." + suffix);
            }
            return new MinioResult(MinioResult.PART_SUCCESS, uuid + "." + suffix);
        } catch (Exception e) {
            this.removeFolder(String.format("temp/%s", uuid));
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 删除文件
     *
     * @param name 文件名称
     * @return 结果
     */
    public boolean delete(String name) {
        try {
            this.minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(name)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 创建文件夹
     *
     * @param path 路径
     * @return 结果
     */
    public boolean createFolder(String path) {
        try {
            path = this.fixPath(path);
            this.minioClient.putObject(PutObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(path)
                    .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean existsFolder(String path) {
        try {
            this.minioClient.statObject(StatObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(path + "/")
                    .build());
        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                return false;
            } else {
                e.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return true;
    }

    /**
     * 删除文件夹
     * -- 删除文件夹在 Minio 里是清空文件下的文件
     *
     * @param path 路径
     * @return 结果
     */
    public boolean removeFolder(String path) {
        try {
            String prefix = path + "/";
            Iterable<Result<Item>> listObjects = this.minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(this.bucket)
                    .prefix(prefix)
                    .build());
            List<DeleteObject> objects = new LinkedList<>();
            listObjects.forEach(item -> {
                try {
                    objects.add(new DeleteObject(item.get().objectName()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            if (objects.size() > 0) {
                Iterable<Result<DeleteError>> results = this.minioClient.removeObjects(RemoveObjectsArgs.builder()
                        .bucket(this.bucket)
                        .objects(objects)
                        .build());
                for (Result<DeleteError> result : results) {
                    DeleteError error = result.get();
                    log.error("删除对象 ---> " + error.objectName() + " 发生错误 --->" + error.message());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private String fixPath(String path) {
        if (!StringUtils.endsWithIgnoreCase(path, "/")) {
            path += "/";
        }
        return path;
    }

    private String uuid() {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        uuid = uuid.substring(10);
        return uuid;
    }

}
