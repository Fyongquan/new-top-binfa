package com.seckill.mapper;

import com.seckill.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券Mapper接口
 * 
 * @author seckill-test
 */
@Mapper
public interface CouponMapper {

  /**
   * 根据ID查询优惠券
   * 
   * @param id 优惠券ID
   * @return 优惠券信息
   */
  Coupon selectById(@Param("id") Long id);

  /**
   * 插入优惠券
   * 
   * @param coupon 优惠券信息
   * @return 影响行数
   */
  int insert(Coupon coupon);

  /**
   * 根据ID更新优惠券
   * 
   * @param coupon 优惠券信息
   * @return 影响行数
   */
  int updateById(Coupon coupon);

  /**
   * 根据ID删除优惠券
   * 
   * @param id 优惠券ID
   * @return 影响行数
   */
  int deleteById(@Param("id") Long id);

  /**
   * 查询所有优惠券
   * 
   * @return 优惠券列表
   */
  List<Coupon> selectAll();

  /**
   * 减少库存（原子操作）
   * 
   * @param id    优惠券ID
   * @param count 减少数量
   * @return 影响行数
   */
  int decreaseStock(@Param("id") Long id, @Param("count") Integer count);

  /**
   * 增加库存（原子操作）
   * 
   * @param id    优惠券ID
   * @param count 增加数量
   * @return 影响行数
   */
  int increaseStock(@Param("id") Long id, @Param("count") Integer count);

  /**
   * 查询有效的优惠券
   * 
   * @return 有效优惠券列表
   */
  List<Coupon> selectValidCoupons();
}
