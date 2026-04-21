package com.hmdp.utils;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    Integer offset;
    List<?> list;
    Long minTime;
}
