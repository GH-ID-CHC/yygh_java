package com.chai.yygh.service;

import com.chai.yygh.model.hosp.Hospital;
import com.chai.yygh.vo.hosp.HospitalQueryVo;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface HospitalService {
    void save(Map<String, Object> switchMap);

    Hospital getHospital(String hoscode);

    Page<Hospital> page(Integer page, Integer limit, HospitalQueryVo hospitalQueryVo);

    void updateStatus(String id, Integer status);


    /**
     * 获取医院信息
     *
     * @param id id
     * @return {@code Hospital}
     */
    Hospital getInfo(String id);
}
