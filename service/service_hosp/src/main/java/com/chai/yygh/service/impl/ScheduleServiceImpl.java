package com.chai.yygh.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chai.yygh.common.exception.YyghException;
import com.chai.yygh.common.result.ResultCodeEnum;
import com.chai.yygh.model.hosp.BookingRule;
import com.chai.yygh.model.hosp.Department;
import com.chai.yygh.model.hosp.Hospital;
import com.chai.yygh.model.hosp.Schedule;
import com.chai.yygh.repository.ScheduleRepository;
import com.chai.yygh.service.DepartmentService;
import com.chai.yygh.service.HospitalService;
import com.chai.yygh.service.ScheduleService;
import com.chai.yygh.vo.hosp.BookingScheduleRuleVo;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import org.bson.Document;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @program: yygh
 * @author:
 * @create: 2023-04-16 21:27
 **/
@Service
public class ScheduleServiceImpl implements ScheduleService {
    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private HospitalService hospitalService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void save(Map<String, Object> switchMap) {
        //将map转换为json字符串再转对象类型
        String jsonString = JSONObject.toJSONString(switchMap);
        Schedule schedule = JSONObject.parseObject(jsonString, Schedule.class);
        String hoscode = schedule.getHoscode();
        String hosScheduleId = schedule.getHosScheduleId();
        Schedule findSchedule = scheduleRepository.getSchduleByHoscodeAndHosScheduleId(hoscode, hosScheduleId);
        if (findSchedule != null) {
            schedule.setId(findSchedule.getId());
            schedule.setCreateTime(findSchedule.getCreateTime());
            schedule.setUpdateTime(new Date());
            schedule.setStatus(findSchedule.getStatus());
            schedule.setIsDeleted(findSchedule.getIsDeleted());
            scheduleRepository.save(schedule);
        } else {
            schedule.setCreateTime(new Date());
            schedule.setUpdateTime(new Date());
            schedule.setIsDeleted(0);
            schedule.setStatus(1);
            scheduleRepository.save(schedule);
        }

    }

    @Override
    public Page<Schedule> page(Map<String, Object> switchMap) {
        System.out.println("swichmap===>"+switchMap);
        //将map转换为json字符串再转对象类型
        String jsonString = JSONObject.toJSONString(switchMap);
        System.out.println(jsonString);
        Schedule schedule = JSONObject.parseObject(jsonString, Schedule.class);
        Integer pageNum = Integer.parseInt((String) switchMap.get("page"));
        Integer pageSize = Integer.parseInt((String) switchMap.get("limit"));
        if (StringUtils.isEmpty(pageNum)) {
            pageNum = 1;
        }
        if (StringUtils.isEmpty(pageSize)) {
            pageSize = 10;
        }
        ExampleMatcher matcher = ExampleMatcher.matching().withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);
        Schedule isSchedule = new Schedule();
        isSchedule.setStatus(1);
        isSchedule.setIsDeleted(0);
        isSchedule.setHoscode(schedule.getHoscode());
        isSchedule.setHosScheduleId(schedule.getHosScheduleId());
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize);
        Example<Schedule> scheduleExample = Example.of(isSchedule, matcher);
        Page<Schedule> all = scheduleRepository.findAll(scheduleExample, pageRequest);
        return all;
    }

    @Override
    public void remove(String hoscode, String hosScheduleId) {
        Schedule schdule = scheduleRepository.getSchduleByHoscodeAndHosScheduleId(hoscode, hosScheduleId);
        if (hosScheduleId != null) {
            scheduleRepository.deleteById(schdule.getId());
        }
    }

    @Override
    public Map<String, Object> getRuleSchedule(long page, long limit, String hoscode, String depcode) {
        //条件
        Criteria criteria = Criteria.where("hoscode").is(hoscode).and("depcode").is(depcode);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                //工作日期进行分组
                Aggregation.group("workDate")
                        .first("workDate").as("workDate")//取别名
                        .count().as("docCount")                    //总记录数
                        .sum("reservedNumber").as("reservedNumber")//可预约数
                        .sum("availableNumber").as("availableNumber"),//剩余预约数
                Aggregation.sort(Sort.Direction.DESC, "workDate"),
                //分页
                Aggregation.skip((page - 1) * limit),
                Aggregation.limit(limit)

        );
        AggregationResults<BookingScheduleRuleVo> aggregate = mongoTemplate.aggregate(aggregation, Schedule.class, BookingScheduleRuleVo.class);
        List<BookingScheduleRuleVo> mappedResults = aggregate.getMappedResults();

        //分组查询的总记录数
        Aggregation totalAgg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("workDate")
        );
        AggregationResults<BookingScheduleRuleVo> totalAggResults =
                mongoTemplate.aggregate(totalAgg,
                        Schedule.class, BookingScheduleRuleVo.class);
        int total = totalAggResults.getMappedResults().size();
        //把日期对应星期获取
        for (BookingScheduleRuleVo mappedResult : mappedResults) {
            Date workDate = mappedResult.getWorkDate();
            String dayOfWeek = this.getDayOfWeek(new DateTime(workDate));
            mappedResult.setDayOfWeek(dayOfWeek);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingScheduleRuleList",mappedResults);
        result.put("total",total);

        //获取医院名称
        String hosName = hospitalService.getHospName(hoscode);
        //其他基础数据
        Map<String, String> baseMap = new HashMap<>();
        baseMap.put("hosname",hosName);
        result.put("baseMap",baseMap);

        return result;
    }

    @Override
    public List<Schedule> getDetailSchedule(String hoscode, String depcode, String workDate) {
        //根据参数查询mongodb
        List<Schedule> scheduleList =
                scheduleRepository.findScheduleByHoscodeAndDepcodeAndWorkDate(hoscode,depcode,new DateTime(workDate).toDate());
        //把得到list集合遍历，向设置其他值：医院名称、科室名称、日期对应星期
        scheduleList.stream().forEach(item->{
            this.packageSchedule(item);
        });
        return scheduleList;
    }

    @Override
    public Map<String, Object> getBookingScheduleRule(int page, int limit, String hoscode, String depcode) {
        Map<String, Object> result = new HashMap<>();

        //获取预约规则
        Hospital hospital = hospitalService.getHospital(hoscode);
        if(null == hospital) {
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        BookingRule bookingRule = hospital.getBookingRule();

        //获取可预约日期分页数据
        IPage iPage = this.getListDate(page, limit, bookingRule);
        //当前页可预约日期
        List<Date> dateList = iPage.getRecords();
        //获取可预约日期科室剩余预约数
        Criteria criteria = Criteria.where("hoscode").is(hoscode).and("depcode").is(depcode).and("workDate").in(dateList);
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("workDate")//分组字段
                        .first("workDate").as("workDate")
                        .count().as("docCount")
                        .sum("availableNumber").as("availableNumber")
                        .sum("reservedNumber").as("reservedNumber")
        );
        AggregationResults<BookingScheduleRuleVo> aggregationResults = mongoTemplate.aggregate(agg, Schedule.class, BookingScheduleRuleVo.class);
        List<BookingScheduleRuleVo> scheduleVoList = aggregationResults.getMappedResults();
        //获取科室剩余预约数

        //合并数据 将统计数据ScheduleVo根据“安排日期”合并到BookingRuleVo
        Map<Date, BookingScheduleRuleVo> scheduleVoMap = new HashMap<>();
        if(!CollectionUtils.isEmpty(scheduleVoList)) {
            scheduleVoMap = scheduleVoList.stream().collect(Collectors.toMap(BookingScheduleRuleVo::getWorkDate, BookingScheduleRuleVo -> BookingScheduleRuleVo));
        }
        //获取可预约排班规则
        List<BookingScheduleRuleVo> bookingScheduleRuleVoList = new ArrayList<>();
        for(int i=0, len=dateList.size(); i<len; i++) {
            Date date = dateList.get(i);

            BookingScheduleRuleVo bookingScheduleRuleVo = scheduleVoMap.get(date);
            if(null == bookingScheduleRuleVo) { // 说明当天没有排班医生
                bookingScheduleRuleVo = new BookingScheduleRuleVo();
                //就诊医生人数
                bookingScheduleRuleVo.setDocCount(0);
                //科室剩余预约数  -1表示无号
                bookingScheduleRuleVo.setAvailableNumber(-1);
            }
            bookingScheduleRuleVo.setWorkDate(date);
            bookingScheduleRuleVo.setWorkDateMd(date);
            //计算当前预约日期为周几
            String dayOfWeek = this.getDayOfWeek(new DateTime(date));
            bookingScheduleRuleVo.setDayOfWeek(dayOfWeek);

            //最后一页最后一条记录为即将预约   状态 0：正常 1：即将放号 -1：当天已停止挂号
            if(i == len-1 && page == iPage.getPages()) {
                bookingScheduleRuleVo.setStatus(1);
            } else {
                bookingScheduleRuleVo.setStatus(0);
            }
            //当天预约如果过了停号时间， 不能预约
            if(i == 0 && page == 1) {
                DateTime stopTime = this.getDateTime(new Date(), bookingRule.getStopTime());
                if(stopTime.isBeforeNow()) {
                    //停止预约
                    bookingScheduleRuleVo.setStatus(-1);
                }
            }
            bookingScheduleRuleVoList.add(bookingScheduleRuleVo);
        }

        //可预约日期规则数据
        result.put("bookingScheduleList", bookingScheduleRuleVoList);
        result.put("total", iPage.getTotal());
        //其他基础数据
        Map<String, String> baseMap = new HashMap<>();
        //医院名称
        baseMap.put("hosname", hospitalService.getHospName(hoscode));
        //科室
        Department department =departmentService.getDepartment(hoscode, depcode);
        //大科室名称
        baseMap.put("bigname", department.getBigname());
        //科室名称
        baseMap.put("depname", department.getDepname());
        //月
        baseMap.put("workDateString", new DateTime().toString("yyyy年MM月"));
        //放号时间
        baseMap.put("releaseTime", bookingRule.getReleaseTime());
        //停号时间
        baseMap.put("stopTime", bookingRule.getStopTime());
        result.put("baseMap", baseMap);
        return result;
    }

    @Override
    public Schedule getById(String id) {
        Schedule schedule = scheduleRepository.findById(id).get();
        return this.packageSchedule(schedule);
    }

    /**
     * 获取可预约日期分页数据
     */
    private IPage<Date> getListDate(int page, int limit, BookingRule bookingRule) {
        //当天放号时间
        DateTime releaseTime = this.getDateTime(new Date(), bookingRule.getReleaseTime());
        //预约周期
        int cycle = bookingRule.getCycle();
        //如果当天放号时间已过，则预约周期后一天为即将放号时间，周期加1
        if (releaseTime.isBeforeNow()) cycle += 1;
        //可预约所有日期，最后一天显示即将放号倒计时
        List<Date> dateList = new ArrayList<>();
        for (int i = 0; i < cycle; i++) {
        //计算当前预约日期
            DateTime curDateTime = new DateTime().plusDays(i);
            String dateString = curDateTime.toString("yyyy-MM-dd");
            dateList.add(new DateTime(dateString).toDate());
        }
        //日期分页，由于预约周期不一样，页面一排最多显示7天数据，多了就要分页显示
        List<Date> pageDateList = new ArrayList<>();
        int start = (page - 1) * limit;
        int end = (page - 1) * limit + limit;
        if (end > dateList.size()) end = dateList.size();
        for (int i = start; i < end; i++) {
            pageDateList.add(dateList.get(i));
        }
        IPage<Date> iPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page(page, 7, dateList.size());
        iPage.setRecords(pageDateList);
        return iPage;
    }
    /**
     * 将Date日期（yyyy-MM-dd HH:mm）转换为DateTime
     */
    private DateTime getDateTime(Date date, String timeString) {
        String dateTimeString = new DateTime(date).toString("yyyy-MM-dd") + " "+ timeString;
        DateTime dateTime = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").parseDateTime(dateTimeString);
        return dateTime;
    }

    private Schedule packageSchedule(Schedule schedule) {
        schedule.getParam().put("hosname",hospitalService.getHospName(schedule.getHoscode()));
        //设置科室名称
        schedule.getParam().put("depname",departmentService.getDepName(schedule.getHoscode(),schedule.getDepcode()));
        //设置日期对应星期
        schedule.getParam().put("dayOfWeek",this.getDayOfWeek(new DateTime(schedule.getWorkDate())));
        return schedule;
    }

    /**
     * 根据日期获取周几数据
     * @param dateTime
     * @return
     */
    private String getDayOfWeek(DateTime dateTime) {
        String dayOfWeek = "";
        switch (dateTime.getDayOfWeek()) {
            case DateTimeConstants.SUNDAY:
                dayOfWeek = "周日";
                break;
            case DateTimeConstants.MONDAY:
                dayOfWeek = "周一";
                break;
            case DateTimeConstants.TUESDAY:
                dayOfWeek = "周二";
                break;
            case DateTimeConstants.WEDNESDAY:
                dayOfWeek = "周三";
                break;
            case DateTimeConstants.THURSDAY:
                dayOfWeek = "周四";
                break;
            case DateTimeConstants.FRIDAY:
                dayOfWeek = "周五";
                break;
            case DateTimeConstants.SATURDAY:
                dayOfWeek = "周六";
            default:
                break;
        }
        return dayOfWeek;
    }
}
