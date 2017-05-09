/**
 * 
 */
package com.sf.vas.mtnvtu.tools;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import com.sf.vas.atjpa.entities.CurrentCycleInfo;
import com.sf.vas.atjpa.entities.CurrentCycleInfo_;
import com.sf.vas.atjpa.entities.Settings;
import com.sf.vas.atjpa.entities.TopUpProfile_;
import com.sf.vas.atjpa.enums.SettingsType;
import com.sf.vas.atjpa.parent.JEntity;
import com.sf.vas.atjpa.tools.QueryService;
import com.sf.vas.mtnvtu.enums.VtuMtnSetting;

/**
 * @author dawuzi
 *
 */
@Stateless
public class VtuMtnQueryService extends QueryService {

	@PostConstruct
	private void initialize(){
//		create all the required settings on init
		for(VtuMtnSetting setting : VtuMtnSetting.values()){
			getSettingValue(setting);
		}
	}
	
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
	
	public String getSettingValue(VtuMtnSetting vtuSetting){
		
		Settings settings = getSettingsByName(vtuSetting.name()); 
		
		if(settings != null){
			return settings.getValue();
		}
		
		settings = createSetting(vtuSetting.name(), vtuSetting.getDefaultValue(), vtuSetting.getDefaultDescription());
		
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
}
