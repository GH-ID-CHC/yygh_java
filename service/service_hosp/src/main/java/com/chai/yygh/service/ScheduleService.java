package com.chai.yygh.service;

import com.chai.yygh.model.hosp.Schedule;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface ScheduleService {
    void save(Map<String, Object> switchMap);

    Page<Schedule> page(Map<String, Object> switchMap);

    void remove(String hoscode, String hosScheduleId);

    /**
     * 获取排版信息
     *
     * @param page    当前页
     * @param limit   记录数
     * @param hoscode 医院编码
     * @param depcode 科室编码
     * @return {@link Map}<{@link String},{@link Object}>
     */
    Map<String,Object> getRuleSchedule(long page, long limit, String hoscode, String depcode);

    /**
     * 获取排班详细时间表
     *
     * @param hoscode  hoscode
     * @param depcode  depcode
     * @param workDate 工作日期
     * @return {@link List}<{@link Schedule}>
     */
    List<Schedule> getDetailSchedule(String hoscode, String depcode, String workDate);

    /**
     * 获取排班可预约日期数据
     * @param page
     * @param limit
     * @param hoscode
     * @param depcode
     * @return
     */
    Map<String, Object> getBookingScheduleRule(int page, int limit, String hoscode, String depcode);

    /**
     * 根据id获取数据
     *
     * @param id id
     * @return {@link Schedule}
     */
    Schedule getById(String id);
}
