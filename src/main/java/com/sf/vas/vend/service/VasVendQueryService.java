/**
 * 
 */
package com.sf.vas.vend.service;

import java.util.List;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.CurrentCycleInfo_;
import com.sf.vas.atjpa.entities.Settings;
import com.sf.vas.atjpa.entities.TopUpProfile_;
import com.sf.vas.atjpa.entities.VtuTransactionLog;
import com.sf.vas.atjpa.entities.VtuTransactionLog_;
import com.sf.vas.atjpa.enums.SettingsType;
import com.sf.vas.atjpa.enums.Status;
import com.sf.vas.atjpa.parent.JEntity;
import com.sf.vas.atjpa.tools.QueryService;
import com.sf.vas.vend.enums.VasVendSetting;

/**
 * @author dawuzi
 *
 */
@Stateless
public class VasVendQueryService extends QueryService {

	public String getSettingValue(String name){
		Settings settings = getSettingsByName(name);
		if(settings == null){
			return null;
		} else {
			return settings.getValue();
		}
	}
	
	public String getSettingValue(String name, String value, String description){
		
		Settings settings = getSettingsByName(name); 
		
		if(settings != null){
			return settings.getValue();
		}
		settings = createSetting(name, value, description);
		
		if(settings == null){
			return null;
		} else {
			return settings.getValue();
		}
	}
	
	public String getSettingValue(VasVendSetting vtuMtnSetting){
		
		Settings settings = getSettingsByName(vtuMtnSetting.name()); 
		
		if(settings != null){
			return settings.getValue();
		}
		
		settings = createSetting(vtuMtnSetting.name(), vtuMtnSetting.getDefaultValue(), vtuMtnSetting.getDefaultDescription());
		
		if(settings == null){
			return null;
		} else {
			return settings.getValue();
		}
	}

	/**
	 * @param vtuSetting
	 * @return
	 */
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	private Settings createSetting(String name, String value, String description) {
		return createSettings(name, value, description, SettingsType.GENERAL);
	}
	
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public <T extends JEntity> T createImmediately(T entity){
		create(entity);
		return entity;
	}
	

	/**
	 * @param pk
	 * @param msisdn
	 * @return
	 */
	public CurrentCycleInfo getCurrentCycleInfo(Long profileId, String msisdn) {
		CriteriaQuery<CurrentCycleInfo> criteriaQuery = criteriaBuilder.createQuery(CurrentCycleInfo.class);
		Root<CurrentCycleInfo> root = criteriaQuery.from(CurrentCycleInfo.class);

		criteriaQuery.select(root);
		criteriaQuery.where(criteriaBuilder.and(
				criteriaBuilder.equal(root.get(CurrentCycleInfo_.msisdn), msisdn),
				criteriaBuilder.equal(root.get(CurrentCycleInfo_.topUpProfile).get(TopUpProfile_.pk), profileId),
				criteriaBuilder.equal(root.get(CurrentCycleInfo_.deleted), false)
				));

		return getSafeSingleResult(criteriaQuery);
	}

	/**
	 * @return
	 */
	public List<VtuTransactionLog> getFailedTransactionLogs() {
		CriteriaQuery<VtuTransactionLog> criteriaQuery = criteriaBuilder.createQuery(VtuTransactionLog.class);
		Root<VtuTransactionLog> root = criteriaQuery.from(VtuTransactionLog.class);
		
		criteriaQuery.select(root);
		criteriaQuery.where(criteriaBuilder.and(
				criteriaBuilder.equal(root.get(VtuTransactionLog_.vtuStatus), Status.FAILED),
				criteriaBuilder.equal(root.get(VtuTransactionLog_.deleted), false)
				));
		
		return entityManager.createQuery(criteriaQuery).getResultList();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> T getByPkWithEagerLoading(Class<T> clazz, 
			long pk, SingularAttribute... attributes){

		CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(clazz);
		Root<T> root = criteriaQuery.from(clazz);

		for (SingularAttribute attribute : attributes){
			root.fetch(attribute, JoinType.LEFT);
		}

		criteriaQuery.select(root);
		criteriaQuery.where(criteriaBuilder.and(
				criteriaBuilder.equal(root.get("pk"), pk),
				criteriaBuilder.equal(root.get("deleted"), false)
				));

		try {
			return entityManager.createQuery(criteriaQuery).getSingleResult();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
}
