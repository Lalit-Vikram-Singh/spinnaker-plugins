/*
 * Copyright 2019 OpsMX, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opsmx.terraspin.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.opsmx.terraspin.component.ApplicationStartup;
import com.opsmx.terraspin.util.ProcessUtil;
import com.opsmx.terraspin.util.TerraAppUtil;

@Component
public class TerraService {

	private static final Logger log = LoggerFactory.getLogger(TerraService.class);

	ApplicationStartup ApplicationStartup = new ApplicationStartup();
	JSONParser parser = new JSONParser();
	/*
	 * @Autowired TerraAppUtil terraAppUtil1;
	 */
	TerraAppUtil terraAppUtil = new TerraAppUtil();

	static String userHomeDir = System.getProperty("user.home");
	static String DEMO_HTML = "<!DOCTYPE html> <html> <head> <meta charset=\"UTF-8\"> <title>Opsmx TerraApp</title> </head> <body bgcolor='#000000'> <pre style=\"color:white;\"> \"OPTION_SCPACE\" </pre> </body> </html>";
	String spinApplicationName = "spinApp";
	String spinPipelineName = "spinPipe";
	String spinpiPelineId = "spinPipeId";
	String spinPlan = System.getenv("plan");
	String spinGitAccount = System.getenv("gitAccount");
	String spincloudAccount = System.getenv("cloudAccount");
	String applicationName = "applicationName-" + spinApplicationName;
	String pipelineName = "pipelineName-" + spinPipelineName;
	String pipelineId = "pipelineId-" + spinpiPelineId;
	
	ProcessUtil processutil = new ProcessUtil();

	@SuppressWarnings("unchecked")
	public String applyStart(String clonerepodir, String baseURL, String variableOverrideFile) {

		File currentTerraformInfraCodeDir = terraAppUtil.createDirForPipelineId(applicationName, pipelineName,
				pipelineId);

		File planPathDir = currentTerraformInfraCodeDir;

		String statusFilePath = planPathDir + "/applyStatus";
		File statusFile = new File(statusFilePath);
		statusFile.delete();
		JSONObject status = new JSONObject();
		status.put("status", "RUNNING");
		InputStream statusInputStream = new ByteArrayInputStream(status.toString().getBytes(StandardCharsets.UTF_8));

		terraAppUtil.writeStreamOnFile(statusFile, statusInputStream);

		String source = clonerepodir;
		File srcDir = new File(source);

		String destination = "/home/terraspin/.opsmx/spinnaker/applicationName-spinApp/pipelineName-spinPipe/pipelineId-spinPipeId";
		File destDir = new File(destination);

		try {
			FileUtils.copyDirectory(srcDir, destDir);
			System.out.println("done!!");
		} catch (IOException e) {
			e.printStackTrace();
		}

		
		boolean ischangemod = processutil.runcommand("chmod 777 -R " + destination);
		log.info("-----ischangemod status ----" + ischangemod);
		
		String tfModulejsonpath = currentTerraformInfraCodeDir + "/.terraform/modules/modules.json";
		String tfModulejson = terraAppUtil.getStrJson(tfModulejsonpath);
		
		
		JSONObject moduleConfigObject = null;
		try {
			moduleConfigObject = (JSONObject) parser.parse(tfModulejson);
		} catch (ParseException pe) {
			log.info("Exception while parsing  tf module json :: " + tfModulejson);
			throw new RuntimeException("config Parse error:", pe);
		}
		
		JSONObject correcttModule = null; 
		JSONArray Modules = (JSONArray) moduleConfigObject.get("Modules");
		for(int i=0; i<Modules.size(); i++) {
			JSONObject currentModule = (JSONObject) Modules.get(i);
			String currentKey = (String) currentModule.get("Key");
			if(StringUtils.equalsAnyIgnoreCase("terraModule", currentKey)) {
				correcttModule = currentModule;
				break;
			}
		}
		
		String tfModuledir = (String) correcttModule.get("Dir");
		
		String exacttfRootModuleFilePathinStr = currentTerraformInfraCodeDir + "/" + tfModuledir;
		File exacttfRootModuleFilePathdir = new File(exacttfRootModuleFilePathinStr);
		
		TerraformApplyThread terraOperationCall = new TerraformApplyThread(exacttfRootModuleFilePathdir,planPathDir,variableOverrideFile);
		Thread trigger = new Thread(terraOperationCall);
		trigger.start();

		try {
			trigger.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String statusPollURL = baseURL + "/api/v1/terraform/applyStatus/" + applicationName + "/" + pipelineName + "/"
				+ pipelineId;

		JSONObject outRootObj = new JSONObject();
		outRootObj.put("status", "RUNNING");
		outRootObj.put("statusurl", statusPollURL);
		log.info("terraform apply status :" + status);
		log.debug("terraform apply status url :" + statusPollURL);
		return outRootObj.toJSONString();
	}

	@SuppressWarnings("unchecked")
	public JSONObject applyStatus(String baseURL) {

		String planPath = System.getProperty("user.home") + "/.opsmx/spinnaker/" + applicationName + "/" + pipelineName
				+ "/" + pipelineId;

		String currentSatusDir = userHomeDir + "/.opsmx/spinnaker/" + applicationName + "/" + pipelineName + "/"
				+ pipelineId + "/applyStatus";

		JSONObject jsonObj = new JSONObject();
		JSONParser parser = new JSONParser();
		String statusStr = new String();

		File planPathDir = new File(planPath);
		JSONObject outputJsonObj = new JSONObject();

		try {
			jsonObj = (JSONObject) parser.parse(new FileReader(currentSatusDir));
			statusStr = (String) jsonObj.get("status");
			if (statusStr.equalsIgnoreCase("running")) {
				outputJsonObj.put("status", "RUNNING");
			} else {
				outputJsonObj.put("status", statusStr);

				String applyOutputURL = baseURL + "/api/v1/terraform/applyOutput/" + applicationName + "/"
						+ pipelineName + "/" + pipelineId;

				JSONObject planExeOutputValuesObject = terraServicePlanExeOutputValues(planPathDir, true);

				outputJsonObj.put("outputValues", planExeOutputValuesObject);
				outputJsonObj.put("applyOutputURL", applyOutputURL);
				log.info("terraform apply status :" + outputJsonObj);
			}

		} catch (Exception e) {
			log.info("Error : terraform apply status");
			throw new RuntimeException("terraform apply status error ", e);

		}
		return outputJsonObj;
	}

	public String applyOutput(String applicationName, String pipelineName, String pipelineId, String baseURL) {

		String currentSatusDir = userHomeDir + "/.opsmx/spinnaker/" + applicationName + "/" + pipelineName + "/"
				+ pipelineId + "/applyStatus";

		JSONObject jsonObj = new JSONObject();
		String statusStr = null;
		JSONParser parser = new JSONParser();
		try {
			jsonObj = (JSONObject) parser.parse(new FileReader(currentSatusDir));
			statusStr = (String) jsonObj.get("output");
		} catch (Exception e) {
			log.info("Error : terraform apply output");
			throw new RuntimeException("terraform apply output error ", e);
		}
		String strToR = DEMO_HTML.replaceAll("OPTION_SCPACE", statusStr);
		log.debug("terraform apply output :" + strToR);
		return strToR;
	}

	@SuppressWarnings("unchecked")
	public JSONObject terraServicePlanExeOutputValues(File terraformCodeDir, boolean isModule) {

		JSONObject planExeOutputValuesJsonObj = new JSONObject();
		Process exec;
		try {

			if (isModule) {
				String exactScriptPath = System.getProperty("user.home") + "/.opsmx/script/exeTerraformGitOutput.sh";
				exec = Runtime.getRuntime().exec(
						new String[] { "/bin/sh", "-c", "sh " + exactScriptPath + " " + terraformCodeDir.getPath() });

			} else {
				String exactScriptPath = System.getProperty("user.home") + "/.opsmx/script/exeTerraformOutput.sh";
				exec = Runtime.getRuntime().exec(
						new String[] { "/bin/sh", "-c", "sh " + exactScriptPath + " " + terraformCodeDir.getPath() });

			}
			exec.waitFor();

			BufferedReader reader = new BufferedReader(new InputStreamReader(exec.getInputStream()));
			String line = "";
			String tempLine = "";
			while ((tempLine = reader.readLine()) != null) {
				String key = tempLine.split("=")[0].trim();
				String value = tempLine.split("=")[1].trim();
				planExeOutputValuesJsonObj.put(key, value);
				line = line + tempLine.trim() + System.lineSeparator();
			}

			BufferedReader reader2 = new BufferedReader(new InputStreamReader(exec.getInputStream()));
			String line2 = "";
			String tempLine2 = "";
			while ((tempLine2 = reader2.readLine()) != null) {
				line2 = line2 + tempLine2.trim() + System.lineSeparator();
			}

			reader.close();
			reader2.close();
			if (!line2.isEmpty())
				log.info("Error : terraform Plan script output values :" + line2);
		} catch (IOException | InterruptedException e) {
			log.info("Error : terraform Plan script OutputValues  ouput");
			throw new RuntimeException("Error : terraform Plan script OutputValues  ouput ", e);
		}
		log.debug("terraform plan output values :" + planExeOutputValuesJsonObj);
		return planExeOutputValuesJsonObj;
	}

	/*
	 * public boolean terraServicePlanSetting(JSONObject halConfigObject, String
	 * spinGitAccount, String spinPlan, String spincloudAccount, boolean
	 * isGitModule, File currentTerraformInfraCodeDir) { String terraformInfraCode =
	 * null;
	 * 
	 * if (StringUtils.isNoneEmpty(spinGitAccount)) { String planConfig = new
	 * String(
	 * "module \"terraModule\"{source = \"git::https://GITUSER:GITPASS@github.com/GITUSER/GITPLANURL\"}"
	 * ); // String gitPlanUrl = spinPlan.split("https://")[1]; String gitPlanUrl =
	 * spinPlan; // JSONObject artifacts = (JSONObject)
	 * halConfigObject.get("artifacts");
	 * 
	 * JSONArray githubArtifactAccounts = (JSONArray) ((JSONObject) ((JSONObject)
	 * halConfigObject.get("artifacts")) .get("github")).get("accounts"); JSONObject
	 * githubArtifactAccount = null;
	 * 
	 * for (int i = 0; i < githubArtifactAccounts.size(); i++) {
	 * githubArtifactAccount = (JSONObject) githubArtifactAccounts.get(i); String
	 * githubArtifactaccountName = (String) githubArtifactAccount.get("name"); if
	 * (StringUtils.equalsIgnoreCase(githubArtifactaccountName.trim(),
	 * spincloudAccount.trim())) break; } String gitUser = (String)
	 * githubArtifactAccount.get("username"); String gittoken = (String)
	 * githubArtifactAccount.get("token"); String gitPass = (String)
	 * githubArtifactAccount.get("password"); isGitModule = true;
	 * 
	 * if (StringUtils.isNoneEmpty(gitPass)) { terraformInfraCode =
	 * planConfig.replaceAll("GITUSER", gitUser).replaceAll("GITPASS", gitPass)
	 * .replaceAll("GITPLANURL", gitPlanUrl); } else { terraformInfraCode =
	 * planConfig.replaceAll("GITUSER", gitUser).replaceAll("GITPASS", gittoken)
	 * .replaceAll("GITPLANURL", gitPlanUrl); }
	 * 
	 * } else { terraformInfraCode = spinPlan; }
	 * 
	 * String infraCodePath = currentTerraformInfraCodeDir.getPath() +
	 * "/infraCode.tf"; File infraCodfile = new File(infraCodePath); if
	 * (!infraCodfile.exists()) { try { infraCodfile.createNewFile(); } catch
	 * (IOException e) { log.info("Error : terraform InfrCodfile Creation"); throw
	 * new RuntimeException("Error : terraform InfrCodfile Creation ", e);
	 * 
	 * } }
	 * 
	 * InputStream infraCodeInputStream = new ByteArrayInputStream(
	 * terraformInfraCode.getBytes(StandardCharsets.UTF_8));
	 * terraAppUtil.overWriteStreamOnFile(infraCodfile, infraCodeInputStream);
	 * 
	 * return isGitModule; }
	 */

}
