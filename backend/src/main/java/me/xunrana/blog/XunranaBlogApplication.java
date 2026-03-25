package me.xunrana.blog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("me.xunrana.blog.mapper")
@EnableScheduling
public class XunranaBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(XunranaBlogApplication.class, args);
    }
}
