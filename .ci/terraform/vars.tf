variable "aws_profile" {
  description = "Name of the public subnet of az"
  default     = "default"
}

variable "region" {
  description = "Region for launching subnets"
  default     = "us-east-1"
}
variable "volumesize" {
  default = 35
}
variable "devicename" {
  default = "/dev/sda1"
}




# EC2 variables
//Run number of test case based on count value: "default = 18" means run all eighteen builds
variable "instance_count" {default = 1 }
variable "instance_name"               { default = "wrf-test" }
variable "instance_profile"            { default = "WRFS3Role" }
variable "ami"                         { default = "ami-06e599aaf4015dafd" }
variable "availability_zone"           { default = ""   }
variable "ebs_optimized"               { default = false  }
variable "instance_type"               { default = "t3a.medium" }
# variable "instance_type_1"             { default = "c5.4xlarge" }
variable "key_name"                    { default = "jenkins" }
variable "monitoring"                  { default = false  }
variable "security_group_ids"          { default = ["sg-0dfbfc9d0b4b1b519"] }
variable "subnet_id"                   { default = "subnet-010ff527a8af9ab9e" }
variable "associate_public_ip_address" { default = false  }
variable "iam_instance_profile"        { default = "" }
variable "user_data"                   { default = " "}
variable "tags"                        { default = {} }
