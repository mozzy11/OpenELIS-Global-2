/**
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is OpenELIS code.
 *
 * Copyright (C) CIRG, University of Washington, Seattle WA.  All Rights Reserved.
 *
 */
package us.mn.state.health.lims.reports.action.implementation;

import static org.apache.commons.validator.GenericValidator.isBlankOrNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.validator.GenericValidator;
import org.jfree.util.Log;

import spring.mine.common.form.BaseForm;
import spring.mine.internationalization.MessageUtil;
import spring.service.project.ProjectService;
import spring.util.SpringContext;
import us.mn.state.health.lims.project.valueholder.Project;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.ARVFollowupColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.ARVInitialColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.CIColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.CSVColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.EIDColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.RTNColumnBuilder;
import us.mn.state.health.lims.reports.action.implementation.reportBeans.VLColumnBuilder;

/**
 * @author Paul A. Hill (pahill@uw.edu)
 * @since Jan 26, 2011
 */
public class ExportProjectByDate extends CSVSampleExportReport implements IReportParameterSetter, IReportCreator {
	protected final ProjectService projectService = SpringContext.getBean(ProjectService.class);
	private String projectStr;
	private Project project;

	@Override
	protected String reportFileName() {
		return "ExportProjectByDate";
	}

	@Override
	public void setRequestParameters(BaseForm form) {
		try {
			PropertyUtils.setProperty(form, "reportName", getReportNameForParameterPage());
			PropertyUtils.setProperty(form, "useLowerDateRange", Boolean.TRUE);
			PropertyUtils.setProperty(form, "useUpperDateRange", Boolean.TRUE);
			PropertyUtils.setProperty(form, "useProjectCode", Boolean.TRUE);
			PropertyUtils.setProperty(form, "projectCodeList", getProjectList());
		} catch (Exception e) {
			Log.error("Error in ExportProjectByDate.setRequestParemeters: ", e);
		}
	}

	protected String getReportNameForParameterPage() {
		return MessageUtil.getMessage("reports.label.project.export") + " "
				+ MessageUtil.getContextualMessage("sample.collectionDate");
	}

	@Override
	protected void createReportParameters() {
		super.createReportParameters();
		reportParameters.put("studyName", (project == null) ? null : project.getLocalizedName());
	}

	@Override
	public void initializeReport(BaseForm form) {
		super.initializeReport();
		errorFound = false;

		lowDateStr = form.getString("lowerDateRange");
		highDateStr = form.getString("upperDateRange");
		projectStr = form.getString("projectCode");
		dateRange = new DateRange(lowDateStr, highDateStr);

		createReportParameters();

		errorFound = !validateSubmitParameters();
		if (errorFound) {
			return;
		}

		createReportItems();
	}

	/**
	 * check everything
	 */
	private boolean validateSubmitParameters() {
		return dateRange.validateHighLowDate("report.error.message.date.received.missing") && validateProject();
	}

	/**
	 * @return true, if location is not blank or "0" is is found in the DB; false
	 *         otherwise
	 */
	private boolean validateProject() {
		if (isBlankOrNull(projectStr) || "0".equals(Integer.getInteger(projectStr))) {
			add1LineErrorMessage("report.error.message.project.missing");
			return false;
		}
		project = projectService.getProjectById(projectStr);
		if (project == null) {
			add1LineErrorMessage("report.error.message.project.missing");
			return false;
		}
		return true;
	}

	/**
	 * creating the list for generation to the report
	 */
	private void createReportItems() {
		try {
			csvColumnBuilder = getColumnBuilder(projectStr);
			csvColumnBuilder.buildDataSource();
		} catch (Exception e) {
			Log.error("Error in " + this.getClass().getSimpleName() + ".createReportItems: ", e);
			add1LineErrorMessage("report.error.message.general.error");
		}
	}

	@Override
	protected void writeResultsToBuffer(ByteArrayOutputStream buffer)
			throws Exception, IOException, UnsupportedEncodingException {

		String currentAccessionNumber = null;
		String[] splitBase = null;
		while (csvColumnBuilder.next()) {
			String line = csvColumnBuilder.nextLine();
			String[] splitLine = line.split(",");

			if (splitLine[0].equals(currentAccessionNumber)) {
				merge(splitBase, splitLine);
			} else {
				if (currentAccessionNumber != null) {
					writeConsolidatedBaseToBuffer(buffer, splitBase);
				}
				splitBase = splitLine;
				currentAccessionNumber = splitBase[0];
			}
		}

		writeConsolidatedBaseToBuffer(buffer, splitBase);
	}

	private void merge(String[] base, String[] line) {
		for (int i = 0; i < base.length; ++i) {
			if (GenericValidator.isBlankOrNull(base[i])) {
				base[i] = line[i];
			}
		}
	}

	protected void writeConsolidatedBaseToBuffer(ByteArrayOutputStream buffer, String[] splitBase)
			throws IOException, UnsupportedEncodingException {

		if (splitBase != null) {
			StringBuffer consolidatedLine = new StringBuffer();
			for (String value : splitBase) {
				consolidatedLine.append(value);
				consolidatedLine.append(",");
			}

			consolidatedLine.deleteCharAt(consolidatedLine.lastIndexOf(","));
			buffer.write(consolidatedLine.toString().getBytes("windows-1252"));
		}
	}

	private CSVColumnBuilder getColumnBuilder(String projectId) {
		String projectTag = CIColumnBuilder.translateProjectId(projectId);
		if (projectTag.equals("ARVB")) {
			return new ARVInitialColumnBuilder(dateRange, projectStr);
		} else if (projectTag.equals("ARVS")) {
			return new ARVFollowupColumnBuilder(dateRange, projectStr);
		} else if (projectTag.equalsIgnoreCase("DBS")) {
			return new EIDColumnBuilder(dateRange, projectStr);
		} else if (projectTag.equalsIgnoreCase("VLS")) {
			return new VLColumnBuilder(dateRange, projectStr);
		} else if (projectTag.equalsIgnoreCase("RTN")) {
			return new RTNColumnBuilder(dateRange, projectStr);
		} else if (projectTag.equalsIgnoreCase("IND")) {
			return new RTNColumnBuilder(dateRange, projectStr);
		}
		throw new IllegalArgumentException();
	}

	/**
	 * @return a list of the correct projects for display
	 */
	protected List<Project> getProjectList() {
		List<Project> projects = new ArrayList<>();
		Project project = new Project();
		project.setProjectName("Antiretroviral Study");
		projects.add(projectService.getProjectByName(project, false, false));
		project.setProjectName("Antiretroviral Followup Study");
		projects.add(projectService.getProjectByName(project, false, false));
		project.setProjectName("Routine HIV Testing");
		projects.add(projectService.getProjectByName(project, false, false));
		project.setProjectName("Early Infant Diagnosis for HIV Study");
		projects.add(projectService.getProjectByName(project, false, false));
		project.setProjectName("Viral Load Results");
		projects.add(projectService.getProjectByName(project, false, false));
		project.setProjectName("Indeterminate Results");
		projects.add(projectService.getProjectByName(project, false, false));
		return projects;
	}
}
