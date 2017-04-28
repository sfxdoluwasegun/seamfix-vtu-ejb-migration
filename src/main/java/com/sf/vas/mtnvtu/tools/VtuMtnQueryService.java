/**
 * 
 */
package com.sf.vas.mtnvtu.tools;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.sf.vas.atjpa.entities.Settings;
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
}
