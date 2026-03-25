package me.xunrana.blog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> from(IPage<T> page) {
        return PageResult.<T>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .page(page.getCurrent())
                .size(page.getSize())
                .build();
    }
}
