package controllers

import models.CompaniesHouseId
import services.CompanySearchResult

object SearchServiceGenTestData {

  case class TestDbData(
                         reportCounts: Map[CompaniesHouseId, Int],
                         countFiledCallCount: Int = 0,
                         evalDbCallCount: Int = 0
                       )

  case class TestData(
                       searchResults: Map[CompaniesHouseId, CompanySearchResult],
                       dbData: TestDbData
                     )

  val companyId1 = CompaniesHouseId("1")
  val companyName1 = "Company 1"
  val detail1 = CompanySearchResult(companyId1, companyName1, "")

  val testData1 = TestData(Map(companyId1 -> detail1), TestDbData(Map(companyId1 -> 6)))

  val companyId2 = CompaniesHouseId("2")
  val companyName2 = "Company 2"
  val detail2 = CompanySearchResult(companyId2, companyName2, "")

  val testData2 = TestData(
    Map(companyId1 -> detail1, companyId2 -> detail2),
    TestDbData(Map(companyId1 -> 6, companyId2 -> 3))
  )
}
